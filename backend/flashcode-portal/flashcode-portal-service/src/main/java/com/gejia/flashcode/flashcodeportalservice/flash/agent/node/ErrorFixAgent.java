package com.gejia.flashcode.flashcodeportalservice.flash.agent.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.gejia.flashcode.flashcodeportalservice.flash.utils.GeneratedAppWriter;
import com.gejia.flashcode.flashcodeportalservice.flash.utils.ModelOutputParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class ErrorFixAgent implements NodeAction {

    private ChatClient chatClient;

    private VectorStore vectorStore;

    public ErrorFixAgent(ChatClient chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    //如何进行错误修复    大模型修复错误    错误是什么  修复的是哪一个应用
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("--- 开始执行ErrorFixAgent ---");
        Map<String, Object> result = new HashMap<>();
        try {
            Long appId = state.value("appId", Long.class).orElse(null);
            String errorType = state.value("errorType", String.class).orElse(null);
            String error = state.value("error", String.class).orElse(null);
            String appType = state.value("appType", String.class).orElse(null);
            String appDoc = state.value("appDoc", String.class).orElse(null);
            Map<String, String> currentFiles = state.value("files", Map.class).orElse(null);
            String fixOutput = errorFix(appId, appType, appDoc, currentFiles, errorType, error); //通过调用大模型进行错误修复
            ModelOutputParser.ParsedResult parsedResult = ModelOutputParser.parse(fixOutput);
            Path appPath = GeneratedAppWriter.writeFiles(appId, parsedResult.getFiles());
            result.put("status", "SUCCESS");
            result.put("fixSuccess", true);
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("fixSuccess", false);
        }
        return result;
    }

    private String errorFix(Long appId, String appType, String appDoc,
                            Map<String, String> currentFiles,
                            String errorType, String error) {
        String systemPrompt = buildFixSystemPrompt();
        String userPrompt = buildFixUserPrompt(error, errorType, currentFiles, appType, appDoc);
        return chatClient.prompt()
                .system(systemPrompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, String.valueOf(appId)))
                .advisors(QuestionAnswerAdvisor
                        .builder(vectorStore)
                        .searchRequest(SearchRequest.builder().build())
                        .build()
                )
                .user(userPrompt).call().content();
    }

    private String buildFixSystemPrompt() {
        return String.join("\n",
                "你是一个专业的代码调试和修复专家，精通全栈开发和错误诊断。",
                "你的任务是分析应用生成或构建过程中出现的错误，并提供完整的修复方案。",
                "",
                "### 错误分析能力",
                "- **编译错误**: 语法错误、类型错误、导入缺失、API 使用错误",
                "- **构建错误**: 依赖缺失、版本冲突、配置错误、打包失败",
                "- **运行时错误**: 启动异常、端口冲突、资源加载失败、配置错误",
                "- **依赖问题**: package.json、pom.xml 中的依赖配置错误",
                "- **配置问题**: vite.config.js、application.properties 等配置文件错误",
                "",
                "### 修复策略",
                "1. **精确定位**: 根据错误信息准确定位问题文件和代码行",
                "2. **最小修改**: 只修改必要的部分，保持其他代码不变",
                "3. **完整输出**: 必须输出所有文件，即使某些文件没有修改",
                "4. **保证质量**: 修复后的代码必须能够编译通过、构建成功、正常运行",
                "",
                "### 常见问题修复模式",
                "#### HTML 应用",
                "- 修复 JavaScript 语法错误",
                "- 修正 DOM 操作错误",
                "- 修复资源引用路径",
                "",
                "#### Vue3 应用",
                "- 修复 package.json 中的依赖版本",
                "- 修正 vite.config.js 配置",
                "- 修复组件语法错误",
                "- 修正路由配置错误",
                "- 添加缺失的依赖",
                "",
                "#### Vue3_Spring 应用",
                "- 前端: 同 Vue3 应用的修复策略",
                "- 后端: 修复 pom.xml 依赖配置",
                "- 后端: 修正 Java 语法错误、注解错误",
                "- 后端: 修复 Spring Boot 配置",
                "- 后端: 确保 Controller 路径正确 (/api 前缀)",
                "",
                "### 输出格式要求 (关键)",
                "你必须严格按照以下格式输出修复后的完整代码：",
                "1. **第一行**: 必须且仅输出 `APP_TYPE=<HTML|VUE3|VUE3_SPRING>`",
                "2. **文件内容**: 紧接着按以下格式输出每个文件：",
                "FILE: <relative_path>",
                "```<language>",
                "<complete_file_content>",
                "```",
                "   - `<relative_path>`: 文件的相对路径（如 `index.html`, `frontend/src/App.vue`, `backend/src/main/resources/application.properties`）。",
                "   - `<complete_file_content>`: **完整**的文件内容，**绝对禁止**省略、使用占位符或 `// ...`。",
                "",
                "### 重要约束",
                "- 必须输出所有文件的完整内容，包括未修改的文件",
                "- 禁止使用占位符、省略号或 `// ...` 来省略代码",
                "- 禁止添加任何解释性文字、注释或修复说明",
                "- 只输出 APP_TYPE 和文件内容，不输出其他任何内容"
        );
    }

    private String buildFixUserPrompt(String errorMessage, String errorType,
                                      Map<String, String> currentFiles, String appType,
                                      String appDoc) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("### 错误信息\n");
        prompt.append("**错误类型**: ").append(errorType).append("\n");
        prompt.append("**错误详情**:\n");
        prompt.append("```\n").append(errorMessage).append("\n```\n\n");

        prompt.append("### 原始需求\n");
        prompt.append(appDoc).append("\n\n");

        prompt.append("### 当前代码\n");
        prompt.append("应用类型: ").append(appType).append("\n");
        prompt.append("文件数量: ").append(currentFiles.size()).append("\n\n");

        // 只包含关键文件内容，避免提示词过长
        Map<String, String> keyFiles = filterKeyFiles(currentFiles, appType);
        for (Map.Entry<String, String> entry : keyFiles.entrySet()) {
            prompt.append("FILE: ").append(entry.getKey()).append("\n");
            prompt.append("```\n").append(entry.getValue()).append("\n```\n\n");
        }

        if (keyFiles.size() < currentFiles.size()) {
            prompt.append("... 还有 ").append(currentFiles.size() - keyFiles.size())
                    .append(" 个文件未显示 ...\n\n");
        }

        prompt.append("### 修复要求\n");
        prompt.append("1. 仔细分析上述错误信息，定位问题根本原因\n");
        prompt.append("2. 修复所有导致错误的代码、配置或依赖问题\n");
        prompt.append("3. 确保修复后的代码能够正常编译、构建和运行\n");
        prompt.append("4. 必须输出所有文件的完整内容，包括未修改的文件\n");
        prompt.append("5. 严格按照系统提示的输出格式返回修复后的代码\n");

        return prompt.toString();
    }

    private Map<String, String> filterKeyFiles(Map<String, String> allFiles, String appType) {
        // 优先级: 配置文件 > 入口文件 > 其它文件
        return allFiles.entrySet().stream()
                .filter(entry -> isKeyFile(entry.getKey(), appType))
                .limit(15) // 最多包含 15 个关键文件
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    /**
     * 判断是否为关键文件
     */
    private boolean isKeyFile(String path, String appType) {
        String lower = path.toLowerCase(Locale.ROOT);

        // 配置文件始终包含
        if (lower.contains("package.json") || lower.contains("pom.xml") ||
                lower.contains("vite.config") || lower.contains("application.properties") ||
                lower.contains("application.yml")) {
            return true;
        }

        // 入口文件
        if (lower.contains("main.") || lower.contains("app.") ||
                lower.contains("index.html") || lower.contains("application.java")) {
            return true;
        }

        // 根据应用类型选择
        switch (appType.toUpperCase()) {
            case "HTML":
                return lower.endsWith(".html");
            case "VUE":
                return lower.endsWith(".vue") || lower.endsWith(".js") ||
                        lower.endsWith(".ts") || lower.contains("router");
            case "VUE_SPRING":
                return lower.endsWith(".vue") || lower.endsWith(".java") ||
                        lower.contains("controller") || lower.contains("service");
            default:
                return true;
        }
    }
}
