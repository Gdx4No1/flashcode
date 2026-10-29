package com.gejia.flashcode.flashcodeportalservice.flash.agent.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gejia.flashcode.flashcodecommondomain.exception.ServiceException;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.entity.App;
import com.gejia.flashcode.flashcodeportalservice.flash.mapper.AppMapper;
import com.gejia.flashcode.flashcodeportalservice.flash.utils.AppBuildUtil;
import com.gejia.flashcode.flashcodeportalservice.flash.utils.FileUtil;
import com.github.dockerjava.api.DockerClient;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
public class BuildPreviewAgent implements NodeAction {

    private DockerClient dockerClient;

    private String containerName;

    private String previewHost;

    private AppMapper appMapper;

    public BuildPreviewAgent(DockerClient dockerClient, String containerName, String previewHost, AppMapper appMapper) {
        this.dockerClient = dockerClient;
        this.containerName = containerName;
        this.previewHost = previewHost;
        this.appMapper = appMapper;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("--- 开始执行BuildPreviewAgent ---");
        Boolean appGenerated = state.value("appGenerated", Boolean.class).orElse(null);
        if (!appGenerated) {
            throw new ServiceException("应用生成失败");
        }

        String appType = state.value("appType", String.class).orElse(null);
        Long appId = state.value("appId", Long.class).orElse(null);
        Path appPath = state.value("appPath", Path.class).orElse(null);
        Map<String, Object> result = new HashMap<>();
        try {
            switch (appType) {
                case "HTML":
                    Path targetPath = FileUtil.ensureAppDir(appId, "user-preview").resolve("dist");
                    if (!Files.exists(targetPath)) {
                        Files.createDirectory(targetPath);
                    }
                    Path indexFile = appPath.resolve("index.html");
                    Path targetFile = targetPath.resolve("index.html");
                    Files.copy(indexFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    break;
                case "VUE":
                    AppBuildUtil.buildVuePro(appId, appPath, "user-preview");
                    break;
                case "VUE_SPRING":
                    Path vueProPath = appPath.resolve("frontend");
                    AppBuildUtil.buildVuePro(appId, vueProPath, "user-preview");
                    Path springProPath = appPath.resolve("backend");
                    AppBuildUtil.buildSpringBoot(appId, springProPath, dockerClient, "user-preview", containerName);
                    break;
                default:
                    throw new ServiceException("不支持的应用类型：" + appType);
            }
            String previewUrl = previewHost + appId + "/#/";
            appMapper.update(new LambdaUpdateWrapper<App>()
                    .eq(App::getId, appId)
                    .set(App::getPreviewUrl, previewUrl));
            result.put("buildPreview", true);
            result.put("status", "SUCCESS");
            result.put("previewUrl", previewUrl);
        } catch (Exception e) {
            //todo: 后续调整
            result.put("buildPreview", false);
            result.put("status", "FAILED");
            result.put("errorType", determineBuildErrorType(e));
            result.put("error", buildDetailedBuildErrorMessage(e, appType));
        }
        return result;
    }

    private String determineBuildErrorType(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return "UNKNOWN_BUILD_ERROR";
        }

        String lowerMsg = message.toLowerCase(Locale.ROOT);
        if (lowerMsg.contains("npm") || lowerMsg.contains("dependency") || lowerMsg.contains("package")) {
            return "DEPENDENCY_ERROR";
        }
        if (lowerMsg.contains("maven") || lowerMsg.contains("pom.xml")) {
            return "MAVEN_BUILD_ERROR";
        }
        if (lowerMsg.contains("compile") || lowerMsg.contains("compilation")) {
            return "COMPILATION_ERROR";
        }
        if (lowerMsg.contains("port") || lowerMsg.contains("address already in use")) {
            return "PORT_CONFLICT_ERROR";
        }
        if (lowerMsg.contains("docker") || lowerMsg.contains("container")) {
            return "DOCKER_ERROR";
        }
        if (lowerMsg.contains("jar") || lowerMsg.contains("java")) {
            return "JAVA_RUNTIME_ERROR";
        }
        if (lowerMsg.contains("vite") || lowerMsg.contains("build failed")) {
            return "FRONTEND_BUILD_ERROR";
        }

        return "BUILD_ERROR";
    }

    private String buildDetailedBuildErrorMessage(Exception e, String appType) {
        StringBuilder sb = new StringBuilder();
        sb.append("应用类型: ").append(appType).append("\n");
        sb.append("错误类型: ").append(e.getClass().getSimpleName()).append("\n");
        sb.append("错误消息: ").append(e.getMessage()).append("\n");

        // 添加堆栈跟踪的前几行
        StackTraceElement[] stackTrace = e.getStackTrace();
        if (stackTrace != null && stackTrace.length > 0) {
            sb.append("堆栈跟踪:\n");
            int lines = Math.min(5, stackTrace.length);
            for (int i = 0; i < lines; i++) {
                sb.append("  at ").append(stackTrace[i].toString()).append("\n");
            }
        }

        // 如果有原因异常，也包含进来
        Throwable cause = e.getCause();
        if (cause != null) {
            sb.append("原因: ").append(cause.getClass().getSimpleName())
                    .append(": ").append(cause.getMessage()).append("\n");
        }

        // 针对不同错误类型添加提示
        String errorType = determineBuildErrorType(e);
        sb.append("\n可能的问题:\n");
        switch (errorType) {
            case "DEPENDENCY_ERROR":
                sb.append("- package.json 中的依赖配置可能有误\n");
                sb.append("- 依赖版本可能不兼容\n");
                sb.append("- npm registry 可能无法访问\n");
                break;
            case "MAVEN_BUILD_ERROR":
                sb.append("- pom.xml 配置可能有误\n");
                sb.append("- Java 版本可能不匹配\n");
                sb.append("- Maven 依赖下载失败\n");
                break;
            case "COMPILATION_ERROR":
                sb.append("- 代码存在语法错误\n");
                sb.append("- 导入的类或方法不存在\n");
                sb.append("- 类型不匹配\n");
                break;
            case "FRONTEND_BUILD_ERROR":
                sb.append("- vite.config.js 配置可能有误\n");
                sb.append("- Vue 组件语法错误\n");
                sb.append("- 资源引用路径错误\n");
                break;
            default:
                sb.append("- 检查日志以获取更多信息\n");
        }

        return sb.toString();
    }
}
