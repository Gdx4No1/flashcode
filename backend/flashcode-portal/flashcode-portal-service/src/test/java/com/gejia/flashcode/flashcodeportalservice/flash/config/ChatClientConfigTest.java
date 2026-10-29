package com.gejia.flashcode.flashcodeportalservice.flash.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.*;

/** 使用本机假模型接口验证 OpenAI 兼容模型接入，不读取真实密钥。 */
class ChatClientConfigTest {
    @Test void selectsPersonalModelWhenOtherChatModelsExist() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().setConversionService(new ApplicationConversionService());
            context.register(ChatClientConfig.class, MultipleModels.class);
            context.refresh();
            assertNotNull(context.getBean("chatClient", org.springframework.ai.chat.client.ChatClient.class));
        }
    }

    @Test void sendsDeepSeekModelAndBearerKeyToConfiguredEndpoint() throws Exception {
        checkModel("deepseek-flash");
    }

    @Test void supportsModelNameOverride() throws Exception {
        checkModel("deepseek-v4-pro");
    }

    private void checkModel(String modelName) throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<JsonNode> request = new AtomicReference<>();
        ObjectMapper mapper = new ObjectMapper();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            request.set(mapper.readTree(exchange.getRequestBody()));
            byte[] body = ("""
                    {"id":"local-test","object":"chat.completion","created":1,"model":"%s",
                    "choices":[{"index":0,"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}],
                    "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                    """).formatted(modelName).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
            exchange.close();
        });
        server.start();
        try {
            ChatClientConfig config = new ChatClientConfig();
            RestClient.Builder rest = RestClient.builder();
            config.aiRestClientCustomizer(Duration.ofSeconds(2), Duration.ofSeconds(5)).customize(rest);
            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .apiKey("test-only-key")
                    .restClientBuilder(rest)
                    .webClientBuilder(WebClient.builder())
                    .build();
            var model = OpenAiChatModel.builder().openAiApi(api).build();
            var client = config.chatClient(model, modelName, java.util.List.of());
            assertEquals("OK", client.prompt().user("hello").call().content());
            assertEquals("/v1/chat/completions", path.get());
            assertEquals("Bearer test-only-key", authorization.get());
            assertEquals(modelName, request.get().get("model").asText());
            assertEquals(8192, request.get().get("max_tokens").asInt());
            assertEquals(0.7, request.get().get("top_p").asDouble(), 0.001);
        } finally {
            server.stop(0);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MultipleModels {
        @Bean("flashcodeChatModel") ChatModel personalModel() { return mock(ChatModel.class); }
        @Bean("dashScopeChatModel") ChatModel otherModel() { return mock(ChatModel.class); }
    }
}
