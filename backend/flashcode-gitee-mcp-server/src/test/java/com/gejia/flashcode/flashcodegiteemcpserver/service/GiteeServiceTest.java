package com.gejia.flashcode.flashcodegiteemcpserver.service;

import com.gejia.flashcode.flashcodegiteemcpserver.config.GiteeConfig;
import com.gejia.flashcode.flashcodegiteemcpserver.config.GiteeMCPServerConfig;
import com.gejia.flashcode.flashcodegiteemcpserver.domain.FileInfoDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** 隔离 HTTP 替身：不读取真实令牌，不连接 Gitee。 */
class GiteeServiceTest {
    @TempDir Path root;
    GiteeConfig config;
    GiteeService service;
    List<Request> requests;
    Function<Request, Response> respond;
    final ObjectMapper json = new ObjectMapper();

    @BeforeEach void setup() {
        requests = new ArrayList<>();
        config = new GiteeConfig();
        config.setRepositoryOwner("personal-test");
        config.setRepositoryName("source-test");
        config.setRepositoryBranch("main");
        config.setAccessToken("fake-test-token");
        config.setUserCodeRoot(root.toString());
        respond = request -> response(request, 200, "{}");
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            requests.add(chain.request());
            return respond.apply(chain.request());
        }).build();
        service = new GiteeService(config, client);
    }

    Response response(Request request, int code, String body) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("local fake response")
                .body(ResponseBody.create(body, MediaType.get("application/json"))).build();
    }

    FileInfoDTO file(String path, String content) {
        var dto = new FileInfoDTO(); dto.setFilePath(path); dto.setContent(content); return dto;
    }

    String body(Request request) throws IOException {
        var buffer = new okio.Buffer(); request.body().writeTo(buffer); return buffer.readUtf8();
    }

    String encoded(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test void writesRequireExplicitAuthorization() {
        assertThrows(IllegalStateException.class, () -> service.commitFile("personal-test", "source-test", "main", "test", List.of(file("42/index.html", "ok"))));
        assertThrows(IllegalStateException.class, () -> service.deleteDirectory("personal-test", "source-test", "main", "42", "test"));
        assertTrue(requests.isEmpty());
    }

    @Test void onlyConfiguredRepositoryAndBranchAreAccessible() {
        config.setWritesEnabled(true);
        for (String[] input : List.of(new String[]{"teacher", "source-test", "main"}, new String[]{"personal-test", "other", "main"}, new String[]{"personal-test", "source-test", "other"})) {
            assertThrows(IllegalArgumentException.class, () -> service.commitFile(input[0], input[1], input[2], "test", List.of(file("42/index.html", "ok"))));
        }
        assertTrue(requests.isEmpty());
    }

    @Test void missingPersonalTokenFailsBeforeHttp() {
        config.setAccessToken("");
        assertThrows(IllegalStateException.class, () -> service.pullUserAppCode("personal-test", "source-test", "main", "42", root.resolve("42").toString()));
        assertTrue(requests.isEmpty());
    }

    @Test void commitsUtf8FilesUsingTeachersCreateFileEndpoint() throws Exception {
        config.setWritesEnabled(true);
        respond = request -> request.method().equals("GET")
                ? response(request, 404, "{}") : response(request, 201, "{}");
        service.commitFile("personal-test", "source-test", "main", "测试", List.of(file("42/中文 文件.html", "你好")));
        assertEquals(2, requests.size());
        var request = requests.getLast();
        assertEquals("POST", request.method());
        assertEquals("fake-test-token", request.url().queryParameter("access_token"));
        assertEquals("/api/v5/repos/personal-test/source-test/contents/42/中文 文件.html", String.join("/", request.url().pathSegments()).transform(s -> "/" + s));
        var node = json.readTree(body(request));
        assertEquals("main", node.path("branch").asText());
        assertEquals(encoded("你好"), node.path("content").asText());
    }

    @Test void validatesAllPathsBeforeFirstRemoteWrite() {
        config.setWritesEnabled(true);
        for (String path : List.of("../outside.txt", "42/../secret", "42/a/../../secret", "42/C:/bad", "42/a\\b", "42//bad")) {
            assertThrows(IllegalArgumentException.class, () -> service.commitFile("personal-test", "source-test", "main", "test", List.of(file("42/ok.txt", "ok"), file(path, "bad"))));
        }
        assertTrue(requests.isEmpty());
    }

    @Test void failedCommitDoesNotReportSuccessOrExposeToken() {
        config.setWritesEnabled(true);
        respond = request -> request.method().equals("GET")
                ? response(request, 404, "{}") : response(request, 403, "{\"message\":\"fake-test-token\"}");
        var failure = assertThrows(IOException.class, () -> service.commitFile("personal-test", "source-test", "main", "test", List.of(file("42/index.html", "ok"))));
        assertTrue(failure.getMessage().contains("403"));
        assertFalse(failure.getMessage().contains("fake-test-token"));
    }

    @Test void updatesExistingFilesUsingGiteesRequiredSha() throws Exception {
        config.setWritesEnabled(true);
        respond = request -> request.method().equals("GET")
                ? response(request, 200, "{\"sha\":\"existing-sha\"}")
                : response(request, 200, "{}");
        service.commitFile("personal-test", "source-test", "main", "update", List.of(file("42/index.html", "new")));
        assertEquals(List.of("GET", "PUT"), requests.stream().map(Request::method).toList());
        assertEquals("existing-sha", json.readTree(body(requests.getLast())).path("sha").asText());
    }

    @Test void pullsDirectoriesUtf8AndEmptyFiles() throws Exception {
        respond = request -> {
            String path = request.url().encodedPath();
            String data = switch (path.substring(path.indexOf("/contents/") + 10)) {
                case "42" -> "[{\"name\":\"src\",\"type\":\"dir\"},{\"name\":\"empty.txt\",\"type\":\"file\"}]";
                case "42/src" -> "[{\"name\":\"main.js\",\"type\":\"file\"}]";
                case "42/src/main.js" -> "{\"content\":\"" + encoded("你好") + "\"}";
                case "42/empty.txt" -> "{\"content\":\"\"}";
                default -> throw new AssertionError(path);
            };
            assertEquals("main", request.url().queryParameter("ref"));
            return response(request, 200, data);
        };
        var target = root.resolve("42");
        assertTrue(service.pullUserAppCode("personal-test", "source-test", "main", "42", target.toString()).contains("2"));
        assertEquals("你好", Files.readString(target.resolve("src/main.js")));
        assertEquals(0, Files.size(target.resolve("empty.txt")));
        try (var files = Files.list(root)) { assertEquals(List.of(target), files.toList()); }
    }

    @Test void failedPullDoesNotPublishPartialSource() throws Exception {
        respond = request -> response(request, 404, "{}");
        var target = root.resolve("42");
        assertThrows(IOException.class, () -> service.pullUserAppCode("personal-test", "source-test", "main", "42", target.toString()));
        assertFalse(Files.exists(target));
        try (var files = Files.list(root)) { assertEquals(0, files.count()); }
    }

    @Test void existingSourceIsNeverDeletedDuringPull() throws Exception {
        var target = Files.createDirectories(root.resolve("42"));
        Files.writeString(target.resolve("index.html"), "existing");
        assertThrows(IOException.class, () -> service.pullUserAppCode("personal-test", "source-test", "main", "42", target.toString()));
        assertEquals("existing", Files.readString(target.resolve("index.html")));
        assertTrue(requests.isEmpty());
    }

    @Test void rejectsOutsideWorkspaceAndInvalidAppId() {
        assertThrows(IOException.class, () -> service.pullUserAppCode("personal-test", "source-test", "main", "42", root.resolve("outside/42").toString()));
        assertThrows(IllegalArgumentException.class, () -> service.pullUserAppCode("personal-test", "source-test", "main", "../42", root.resolve("42").toString()));
        assertTrue(requests.isEmpty());
    }

    @Test void traversalFromRemoteMetadataCannotEscapeStaging() {
        respond = request -> response(request, 200, "[{\"name\":\"../escape.txt\",\"type\":\"file\"}]");
        assertThrows(IOException.class, () -> service.pullUserAppCode("personal-test", "source-test", "main", "42", root.resolve("42").toString()));
        assertFalse(Files.exists(root.resolve("escape.txt")));
    }

    @Test void deleteIsLimitedToOneAppDirectoryAndUsesItsSha() throws Exception {
        config.setWritesEnabled(true);
        respond = request -> {
            if (request.method().equals("DELETE")) return response(request, 200, "{}");
            if (request.url().encodedPath().endsWith("/42/index.html")) return response(request, 200, "{\"sha\":\"test-sha\"}");
            return response(request, 200, "[{\"name\":\"index.html\",\"type\":\"file\"}]");
        };
        service.deleteDirectory("personal-test", "source-test", "main", "42", "delete test app");
        assertEquals(List.of("GET", "GET", "DELETE"), requests.stream().map(Request::method).toList());
        assertEquals("test-sha", json.readTree(body(requests.getLast())).path("sha").asText());
        requests.clear();
        for (String path : List.of("", "/", "..", "42/..", "42/src")) {
            assertThrows(IllegalArgumentException.class, () -> service.deleteDirectory("personal-test", "source-test", "main", path, "bad"));
        }
        assertTrue(requests.isEmpty());
    }

    @Test void exposesExactlyTeachersThreeMcpTools() {
        var callbacks = new GiteeMCPServerConfig().giteeTools(service).getToolCallbacks();
        assertEquals(Set.of("commitFile", "pullUserAppCode", "deleteDirectory"),
                new HashSet<>(Arrays.stream(callbacks).map(t -> t.getToolDefinition().name()).toList()));
    }
}
