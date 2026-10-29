package com.gejia.flashcode.flashcodegiteemcpserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** 验证模块在本项目依赖下可启动；禁用 Nacos，不读取服务器配置。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.main.web-application-type=reactive",
        "spring.ai.mcp.server.name=flashcode-personal-test",
        "spring.ai.mcp.server.version=1.0.0",
        "spring.ai.mcp.server.type=SYNC",
        "spring.ai.mcp.server.protocol=STREAMABLE",
        "gitee.access-token=",
        "gitee.writes-enabled=false"
})
class GiteeMcpStartupTest {
    @Autowired ToolCallbackProvider provider;
    @LocalServerPort int port;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Test void startsWithThreeToolsAndWithoutCredentials() {
        assertEquals(3, provider.getToolCallbacks().length);
    }

    @Test void servesTheTeachersToolsOverStreamableHttpMcp() {
        var client = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
        var initialize = client.post().uri("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .bodyValue(Map.of("jsonrpc", "2.0", "id", 1, "method", "initialize",
                        "params", Map.of("protocolVersion", "2025-03-26", "capabilities", Map.of(),
                                "clientInfo", Map.of("name", "flashcode-test", "version", "1.0"))))
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath("$.result.protocolVersion").isNotEmpty();
    }

    @Test void listsTheTeacherToolsUsingMcpSession() throws Exception {
        var client = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
        var initialize = client.post().uri("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .bodyValue(Map.of("jsonrpc", "2.0", "id", 3, "method", "initialize",
                        "params", Map.of("protocolVersion", "2025-03-26", "capabilities", Map.of(),
                                "clientInfo", Map.of("name", "flashcode-test", "version", "1.0"))))
                .exchange().expectStatus().is2xxSuccessful().expectBody().returnResult();
        String session = initialize.getResponseHeaders().getFirst("mcp-session-id");
        assertNotNull(session);
        String response = client.post().uri("/mcp")
                .header("mcp-session-id", session)
                .header("mcp-protocol-version", "2025-03-26")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .bodyValue(Map.of("jsonrpc", "2.0", "id", 4, "method", "tools/list", "params", Map.of()))
                .exchange().expectStatus().is2xxSuccessful()
                .expectBody(String.class).returnResult().getResponseBody();
        assertNotNull(response);
        String json = response.lines()
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length()).trim())
                .findFirst()
                .orElse(response.trim());
        JsonNode tools = objectMapper.readTree(json).path("result").path("tools");
        assertEquals(3, tools.size());
        assertTrue(hasTool(tools, "commitFile"));
        assertTrue(hasTool(tools, "pullUserAppCode"));
        assertTrue(hasTool(tools, "deleteDirectory"));
    }

    private boolean hasTool(JsonNode tools, String name) {
        for (JsonNode tool : tools) {
            if (name.equals(tool.path("name").asText())) return true;
        }
        return false;
    }
}
