package com.gejia.flashcode.flashcodeportalservice.flash.config;

import com.gejia.flashcode.flashcodeportalservice.flash.utils.GenerateAppWriter;
import com.gejia.flashcode.flashcodeportalservice.flash.utils.ModelOutPutParser;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import static org.junit.jupiter.api.Assertions.*;

/** 仅显式开启时调用个人模型，普通单元测试不联网、不使用真实密钥。 */
@EnabledIfEnvironmentVariable(named = "FLASHCODE_LIVE_MODEL", matches = "true")
class LiveHtmlModelTest {
    @TempDir Path directory;

    @Test
    @ResourceLock("user.dir")
    void generatesAndPublishesRealModelHtml() throws Exception {
        String key = System.getenv("DEEPSEEK_API_KEY");
        assertNotNull(key, "请通过环境变量提供模型密钥");
        ChatClientConfig config = new ChatClientConfig();
        RestClient.Builder rest = RestClient.builder();
        config.aiRestClientCustomizer(Duration.ofSeconds(15), Duration.ofMinutes(2)).customize(rest);
        var api = OpenAiApi.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey(key)
                .restClientBuilder(rest)
                .webClientBuilder(WebClient.builder())
                .build();
        var model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model("deepseek-flash").maxTokens(4096).build())
                .build();
        var response = config.chatClient(model, "deepseek-flash", java.util.List.of()).prompt()
                .system("输出一个不依赖外部资源、少于20行的完整HTML。第一行APP_TYPE=HTML，第二行FILE: index.html，然后使用html代码围栏输出完整代码。不要解释。")
                .user("创建标题为Flashcode的简单个人介绍页，作者葛佳。")
                .call().chatResponse();
        String output = response.getResult().getOutput().getText();
        assertNotNull(output, "模型返回正文为空；检查输出 token 上限和模型推理参数");
        assertFalse(output.isBlank(), "模型返回正文为空；检查输出 token 上限和模型推理参数");
        var parsed = ModelOutPutParser.parse(output);
        ModelOutPutParser.validate(parsed);
        String previous = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", directory.toString());
            Path source = GenerateAppWriter.writeFiles(101L, parsed.getFiles());
            Path preview = directory.resolve("preview/101");
            GenerateAppWriter.publishDirectory(source, preview);
            String html = Files.readString(preview.resolve("index.html"));
            assertTrue(html.toLowerCase().contains("<html"));
            assertTrue(html.contains("Flashcode"));
            assertEquals(Files.readString(source.resolve("index.html")), html);
        } finally {
            System.setProperty("user.dir", previous);
        }
    }
}
