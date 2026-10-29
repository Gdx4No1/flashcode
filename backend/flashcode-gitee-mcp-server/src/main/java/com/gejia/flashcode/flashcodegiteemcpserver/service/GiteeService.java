package com.gejia.flashcode.flashcodegiteemcpserver.service;

import com.gejia.flashcode.flashcodegiteemcpserver.config.GiteeConfig;
import com.gejia.flashcode.flashcodegiteemcpserver.domain.FileInfoDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GiteeService {

    @Autowired
    private GiteeConfig giteeConfig;
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private String buildUrl(String path) {
        return giteeConfig.getApiBaseUrl() + path + "?access_token=" + giteeConfig.getAccessToken();
    }

    @Tool(description = "批量提交文件")
    public String commitFile(
            @ToolParam(description = "仓库所有者用户名") String owner,
            @ToolParam(description = "仓库名称") String repo,
            @ToolParam(description = "目标分支") String branch,
            @ToolParam(description = "提交信息") String message,
            @ToolParam(description = "文件列表") List<FileInfoDTO> files
    ) throws Exception {
        log.info("commitFile: owner={}, repo={}, branch={}, message={}, files={}", owner, repo, branch, message, files);
        List<String> results = new ArrayList<>();
        for (FileInfoDTO file : files) {
            String result = createFile(owner, repo, branch, file.getFilePath(), message, file.getContent(), "text");
            results.add(result);
        }

        return mapper.writeValueAsString(results);
    }

    @Tool(description = "从 flash-app-code 仓库同步指定 appId 的代码到目标目录")
    public String pullUserAppCode(
            @ToolParam(description = "仓库所有者用户名") String owner,
            @ToolParam(description = "仓库名称") String repo,
            @ToolParam(description = "目标分支") String branch,
            @ToolParam(description = "应用 ID，对应仓库中的目录") String appId,
            @ToolParam(description = "本地目标目录，用于落地用户代码") String targetDir
    ) throws Exception {
        log.info("pullUserAppCode owner={}, repo={}, branch={}, appId={}, targetDir={}", owner, repo, branch, appId, targetDir);
        if (owner == null || owner.isBlank() ||
                repo == null || repo.isBlank() ||
                appId == null || appId.isBlank() ||
                targetDir == null || targetDir.isBlank()) {
            throw new IllegalArgumentException("owner、repo、appId、targetDir 不能为空");
        }

        Path localDir = Paths.get(targetDir).toAbsolutePath().normalize();
        Files.createDirectories(localDir);
        cleanDirectory(localDir);

        AtomicInteger fileCounter = new AtomicInteger();
        downloadDirectory(owner, repo, branch, appId, localDir, fileCounter);

        log.info("--- 文件同步完成 ---");
        return String.format("同步完成，写入 %d 个文件到 %s", fileCounter.get(), localDir);
    }

    @Tool(description = "删除指定仓库分支下的目录（递归删除目录下所有文件）")
    public String deleteDirectory(
            @ToolParam(description = "仓库所有者用户名") String owner,
            @ToolParam(description = "仓库名称") String repo,
            @ToolParam(description = "分支") String branch,
            @ToolParam(description = "要删除的目录路径") String dirPath,
            @ToolParam(description = "提交信息") String message
    ) throws Exception {

        log.info("deleteDirectory owner={}, repo={}, branch={}, dirPath={}", owner, repo, branch, dirPath);

        if (owner == null || owner.isBlank() ||
                repo == null || repo.isBlank() ||
                dirPath == null || dirPath.isBlank()) {
            throw new IllegalArgumentException("owner、repo、dirPath 不能为空");
        }

        List<String> deletedFiles = new ArrayList<>();

        // 递归删除
        deleteDirectoryRecursive(owner, repo, branch, dirPath, message, deletedFiles);

        return mapper.writeValueAsString(deletedFiles);
    }

    private String createFile(String owner, String repo, String branch, String path, String message, String content, String encoding) throws Exception {
        String url = buildUrl("/repos/" + owner + "/" + repo + "/contents/" + normalizePath(path));
        String bodyJson = mapper.createObjectNode()
                .put("message", message)
                .put("branch", branch)
                .put("content", encodeContent(content, encoding))
                .toString();
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(bodyJson, MediaType.parse("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    private String encodeContent(String content, String encoding) {
        if (content == null) return "";
        if (encoding != null && encoding.equalsIgnoreCase("base64")) {
            return content;
        }
        return Base64.getEncoder().encodeToString(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String normalizePath(String path) {
        if (path == null) return "";
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private void cleanDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            return;
        }
        List<Path> paths = Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
        for (Path path : paths) {
            if (!path.equals(dir)) {
                Files.deleteIfExists(path);
            }
        }
    }

    private void downloadDirectory(String owner, String repo, String branch,
                                   String remotePath, Path localDir, AtomicInteger fileCounter) throws Exception {
        String url = buildContentsUrl(owner, repo, remotePath, branch);
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("拉取目录失败: " + remotePath + ", code=" + response.code());
            }
            String body = response.body().string();
            JsonNode node = mapper.readTree(body);
            if (!node.isArray()) {
                throw new IOException("远程目录不存在或不是目录: " + remotePath);
            }
            Files.createDirectories(localDir);
            for (JsonNode child : node) {
                String name = child.path("name").asText();
                String type = child.path("type").asText();
                String childRemotePath = remotePath == null || remotePath.isBlank()
                        ? name : remotePath + "/" + name;
                Path target = localDir.resolve(name);
                if ("dir".equalsIgnoreCase(type)) {
                    downloadDirectory(owner, repo, branch, childRemotePath, target, fileCounter);
                } else if ("file".equalsIgnoreCase(type)) {
                    log.info("--- 下载文件 ---");
                    downloadFile(owner, repo, branch, childRemotePath, target);
                    fileCounter.incrementAndGet();
                }
            }
        }
    }

    private void downloadFile(String owner, String repo, String branch,
                              String remotePath, Path destination) throws Exception {
        String url = buildContentsUrl(owner, repo, remotePath, branch);
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("拉取文件失败: " + remotePath + ", code=" + response.code());
            }
            String body = response.body().string();
            JsonNode node = mapper.readTree(body);
            String content = node.path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new IOException("未获取到文件内容: " + remotePath);
            }
            log.info("文件内容 {}",content);
            byte[] data = Base64.getDecoder().decode(content.replace("\n", ""));
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
            log.info("文件写入地址 {}",destination.toAbsolutePath());
            Files.write(destination, data);
        }
    }

    private String buildContentsUrl(String owner, String repo, String path, String branch) {
        String normalizedPath = normalizePath(path);
        String basePath = "/repos/" + owner + "/" + repo + "/contents";
        if (!normalizedPath.isEmpty()) {
            basePath += "/" + normalizedPath;
        }
        String url = buildUrl(basePath);
        if (branch != null && !branch.isBlank()) {
            url = url + "&ref=" + branch;
        }
        return url;
    }

    private void deleteDirectoryRecursive(String owner, String repo, String branch,
                                          String remotePath, String message,
                                          List<String> deletedFiles) throws Exception {

        String url = buildContentsUrl(owner, repo, remotePath, branch);

        Request request = new Request.Builder().url(url).get().build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new IOException("获取目录失败: " + remotePath + ", code=" + response.code());
            }

            JsonNode node = mapper.readTree(response.body().string());

            if (!node.isArray()) {
                throw new IOException("路径不是目录: " + remotePath);
            }

            for (JsonNode child : node) {
                String name = child.path("name").asText();
                String type = child.path("type").asText();

                String childPath = remotePath + "/" + name;

                if ("dir".equalsIgnoreCase(type)) {
                    // 递归删除子目录
                    deleteDirectoryRecursive(owner, repo, branch, childPath, message, deletedFiles);
                } else if ("file".equalsIgnoreCase(type)) {
                    // 删除文件
                    deleteFile(owner, repo, branch, childPath, message);
                    deletedFiles.add(childPath);
                }
            }
        }
    }

    private void deleteFile(String owner, String repo, String branch,
                            String filePath, String message) throws Exception {

        // 1. 先获取文件 sha
        String getUrl = buildContentsUrl(owner, repo, filePath, branch);

        Request getRequest = new Request.Builder().url(getUrl).get().build();

        String sha;

        try (Response response = client.newCall(getRequest).execute()) {

            if (!response.isSuccessful()) {
                throw new IOException("获取文件信息失败: " + filePath);
            }

            JsonNode node = mapper.readTree(response.body().string());
            sha = node.path("sha").asText();

            if (sha == null || sha.isBlank()) {
                throw new IOException("未获取到 sha: " + filePath);
            }
        }

        // 2. 调用删除接口
        String deleteUrl = buildUrl("/repos/" + owner + "/" + repo + "/contents/" + normalizePath(filePath));

        String bodyJson = mapper.createObjectNode()
                .put("message", message)
                .put("sha", sha)
                .put("branch", branch)
                .toString();

        Request deleteRequest = new Request.Builder()
                .url(deleteUrl)
                .delete(RequestBody.create(bodyJson, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(deleteRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("删除文件失败: " + filePath + ", code=" + response.code());
            }
        }
    }
}

