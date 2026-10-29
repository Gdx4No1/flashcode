package com.gejia.flashcode.flashcodeportalservice.flash.agent.node;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gejia.flashcode.flashcodeportalservice.flash.advisor.TokenUsageAdvisor;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.entity.App;
import com.gejia.flashcode.flashcodeportalservice.flash.eums.AppType;
import com.gejia.flashcode.flashcodeportalservice.flash.mapper.AppMapper;
import com.gejia.flashcode.flashcodeportalservice.flash.utils.GeneratedAppWriter;
import com.gejia.flashcode.flashcodeportalservice.flash.utils.ModelOutputParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.content.Media;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.util.MimeType;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


//总 分  总
@Slf4j
public class AppGenerationAgent implements NodeAction {

    private AppMapper appMapper;

    private ChatClient chatClient;

    private VectorStore vectorStore;

    public AppGenerationAgent(AppMapper appMapper, ChatClient chatClient, VectorStore vectorStore) {
        this.appMapper = appMapper;
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("--- 开始执行AppGenerationAgent ---");
        Map<String, Object> result = new HashMap<>();
        try {
            Long appId = state.value("appId", Long.class).orElse(null);
            String appDoc = state.value("appDoc", String.class).orElse(null);
            String sketch = state.value("sketch", String.class).orElse(null);
            String contentType = state.value("contentType", String.class).orElse(null);
            Long userId = state.value("userId", Long.class).orElse(null);
            String chatModelName = state.value("chatModelName", String.class).orElse(null);
            String systemPrompt = composeSystemPrompt(appId);
            String userPrompt = composeUserPrompt(appDoc);
//        - HTML：整个应用只有一个HTML页面，不依赖任何后端服务。   通过浏览器访问 能正常访问
//        - VUE：基于 Vue3 构建的纯前端应用，不依赖后端服务。      编译 运行 =》 通过浏览器访问 能正常访问
//        - VUE_SPRING：完整的前后端分离应用，前端基于 Vue3 架构，后端采用 SpringBoot 框架。   分别编译运行前后端工程   =》 通过浏览器访问 能正常访问
            String outPut = generateAppWithAI(systemPrompt, userPrompt, appId, sketch, contentType, userId, chatModelName);
            ModelOutputParser.ParsedResult parsedResult = ModelOutputParser.parse(outPut);
            //update app set app_type = 实际的应用的类型 where id = 实际的应用id
            appMapper.update(new LambdaUpdateWrapper<App>()
                    .eq(App::getId, appId)
                    .set(App::getAppType, AppType.getValue(parsedResult.getAppType())));
            Path appPath = GeneratedAppWriter.writeFiles(appId, parsedResult.getFiles());
            result.put("appType", parsedResult.getAppType());
            result.put("appPath", appPath);
            result.put("files", parsedResult.getFiles());
            result.put("appGenerated", true);
            result.put("status", "SUCCESS");
        } catch (Exception e) {
            result.put("appGenerated", false);
            result.put("error", e.getMessage());
            result.put("status", "FAILED");
        }
        return result;
    }

    private String composeSystemPrompt(Long appId) {
        return String.join("\n",
                "你是资深全栈工程师和架构师，精通现代 Web 开发。你的目标是严格依据用户需求文档生成完整、可运行、代码整洁且页面美观的应用代码。",
                "### 应用类型决策",
                "根据用户需求文档选择最合适的一种应用类型进行生成，注意仅可选择以下三种应用类型",
                "1. **HTML**: 用户明确指出或需求简单，仅需展示或简单交互。",
                "2. **VUE3**: 用户明确指出或需求涉及复杂交互、多页面路由或组件化开发，但无需后端服务。",
                "3. **VUE3_SPRING**: 用户明确指出或需求文档中明确需要后端逻辑。",
                "### 通用生成规范",
                "- **UI/UX**: 若提供图片资源，需根据所提供的图片严格还原布局、组件、配色与交互流程。",
                "- **复杂逻辑**: 生成的所有应用不要包含复杂逻辑（例如：身份认证等）。",
                "- **数据存储**: 生成的所有应用数据存储不依赖任何第三方存储机制。",
                "### 类型详细规范",
                "#### 1. 单个 HTML 页面 (HTML)",
                "- **结构**: 仅输出一个 `index.html` 文件。",
                "- **技术**: 只能使用 HTML、CSS 和原生 JavaScript。禁止引入外部 CSS/JS 库（如 Bootstrap, jQuery）。",
                "- **实现**: CSS 必须内联在 `<head><style>` 中；JS 必须内联在 `</body>` 前的 `<script>` 中。",
                "#### 2. Vue3 工程 (VUE3)",
                "- **技术栈**: Vue 3 (Composition API, `<script setup>`), Vite, Vue Router 4.x。",
                "- **文件结构**: 必须包含标准工程结构（`package.json`, `vite.config.js`, `index.html`, `src/main.js`, `src/App.vue` 等）。",
                "- **配置强制要求**:",
                "  - `vite.config.js`: 必须配置 `base: './'`，配置 `@` 别名指向 `./src`。",
                "  - `router`: 必须使用 `createWebHashHistory()`。",
                "  - `package.json`: 必须包含 `dev` (`vite`) 和 `build` (`vite build`) 脚本。",
                "- **质量保证**:",
                "  - 必须能够通过`npm install`安装项目所需依赖，并且能够通过`npm run build`正确完成构建生成dist目录",
                "#### 3. Vue3 + SpringBoot 工程 (VUE3_SPRING)",
                "- **目录结构**: 前端代码置于 `frontend/` 目录下，后端代码置于 `backend/` 目录下。",
                "- **前端部分 (frontend/)**: ",
                "  - 遵循上述 **VUE3** 的所有规范。",
                "  - **API 请求关键**: 前端请求后端接口时，URL **必须**统一添加前缀 `/" + appId + "/api` (例如 `/" + appId + "/api/users`)。这是网关转发规则，务必遵守。",
                "- **后端部分 (backend/)**: ",
                "  - **技术栈**: Spring Boot 3.x 、JDK 21、 Maven3.9。",
                "  - **代码规范**: 务必通过java自身语法完成代码不要引入其它资源",
                "  - **文件结构**: 必须包含标准工程结构（`pom.xml`, `xxxApplication.java(启动类)` 等）。",
                "  - **核心依赖**: `pom.xml` 必须继承 `spring-boot-starter-parent`，引入 `spring-boot-starter-web`。",
                "  - **构建配置**: `pom.xml` 必须包含 `spring-boot-maven-plugin` 以支持 `java -jar` 运行。",
                "  - **代码实现**: 所有 Controller 的 `@RequestMapping` 必须以 `/api` 开头 (例如 `@RequestMapping(\"/api/users\")`)。注意此处不加：" + appId,
                "  - **数据存储**: **严禁**依赖 MySQL/Redis 等外部服务。仅使用内存 (`ConcurrentHashMap`) 或本地文件模拟数据库。",
                "  - **启动类**: 必须包含标准的 SpringBoot 启动类。",
                "- **质量保证**:",
                "  - 必须能够通过`mvn clean package -DskipTests`生成jar，并且能够通过`java -jar`正确启动jar包",
                "### 输出格式约束 (CRITICAL)",
                "你必须严格按照以下格式输出，解析器依赖此格式：",
                "1. **第一行**: 特别注意必须在第一行输出生成应用的类型（例如：APP_TYPE=HTML、APP_TYPE=VUE3、APP_TYPE=VUE3_SPRING）。",
                "2. **文件内容**: 紧接着按以下格式输出每个文件：",
                "FILE: <relative_path>",
                "```<language>",
                "<complete_file_content>",
                "```",
                "   - `<relative_path>`: 文件的相对路径（如 `index.html`, `frontend/src/App.vue`, `backend/src/main/resources/application.properties`）。",
                "   - `<complete_file_content>`: **完整**的文件内容，**绝对禁止**省略、使用占位符或 `// ...`。"
        );
    }

    private String composeUserPrompt(String appDoc) {
        return String.join("\n",
                "【用户需求文档】",
                appDoc,
                "【输出要求】请严格按照系统提示的格式输出，不要添加多余解释。"
        );
    }

    private String generateAppWithAI(String systemPrompt, String userPrompt, Long appId, String sketch, String contentType, Long userId, String chatModelName) {
        String conversationId = String.valueOf(appId);
        if (sketch != null) {
            byte[] bytes = null;
            try {
                bytes = Files.readAllBytes(Paths.get(sketch));
                String base64 = Base64.getEncoder().encodeToString(bytes);
                String dataUrl = "data:" + contentType + ";base64," + base64;
                Media media = new Media(MimeType.valueOf(Objects.requireNonNull(contentType)), URI.create(dataUrl));
                return chatClient.prompt()
                        .system(systemPrompt)
                        .user(user -> user.text(userPrompt).media(media))
                        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId)
                                .param(TokenUsageAdvisor.TAG_APP_ID, appId)
                                .param(TokenUsageAdvisor.TAG_USER_ID, userId)
                                .param(TokenUsageAdvisor.TAG_MODEL, chatModelName))
                        .options(DashScopeChatOptions.builder()
                                .withModel("qwen-vl-max-latest")
                                .withTopP(0.7)
                                .withMultiModel(true)
                                .build())
                        .call()
                        .content();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId)
                        .param(TokenUsageAdvisor.TAG_APP_ID, appId)
                        .param(TokenUsageAdvisor.TAG_USER_ID, userId)
                        .param(TokenUsageAdvisor.TAG_MODEL, chatModelName))
                .advisors(QuestionAnswerAdvisor
                        .builder(vectorStore)
                        .searchRequest(SearchRequest.builder().build())
                        .build()
                )
                .call()
                .content();
    }


}
