package com.gejia.flashcode.flashcodeportalservice.flash.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gejia.flashcode.flashcodecommondomain.domain.ResultCode;
import com.gejia.flashcode.flashcodecommondomain.exception.ServiceException;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.dto.AppEditDTO;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.dto.GenerateAppResDTO;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.entity.App;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.entity.ContainerCreationResult;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.entity.DockerBindConfig;
import com.gejia.flashcode.flashcodeportalservice.flash.eums.AppType;
import com.gejia.flashcode.flashcodeportalservice.flash.eums.DeployStatus;
import com.gejia.flashcode.flashcodeportalservice.flash.eums.PreviewDeployPath;
import com.gejia.flashcode.flashcodeportalservice.flash.mapper.AppMapper;
import com.gejia.flashcode.flashcodeportalservice.flash.service.IAppGenerationService;
import com.gejia.flashcode.flashcodeportalservice.flash.service.IGiteeService;
import com.gejia.flashcode.flashcodeportalservice.flash.utils.AppBuildUtil;
import com.gejia.flashcode.flashcodeportalservice.flash.utils.FileUtil;
import com.gejia.flashcode.flashcodeportalservice.flash.utils.GeneratedAppWriter;
import com.gejia.flashcode.flashcodeportalservice.flash.utils.ModelOutputParser;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
@Service
public class AppGenerationServiceImpl implements IAppGenerationService {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private IGiteeService giteeService;

    @Autowired
    private DockerClient dockerClient;

    @Autowired
    private VectorStore vectorStore;

    @Value("${app.preview.host}")
    private String previewHost;

    @Value("${app.preview.container-name}")
    private String containerName;

    @Value("${vscode.host}")
    private String vscodeHost;

    @Value("${vscode.port}")
    private int vscodePort;

    @Value("${app.deploy.containerName}")
    private String deployContainerName;

    @Value("${app.deploy.deployPath}")
    private String deployPath;

    @Value("${app.deploy.nginxPath}")
    private String nginxPath;

    @Value("${app.deploy.maxRetries:10}")
    private int maxRetries;

    @Value("${app.imageName}")
    private String imageName;

    @Value("${app.deploy.host}")
    private String deployHost;

    @Override
    public GenerateAppResDTO appGenerate(Long appId, String appDoc) {
        String systemPrompt = composeSystemPrompt(appId);
        String userPrompt = composeUserPrompt(appDoc);
//        - HTML：整个应用只有一个HTML页面，不依赖任何后端服务。   通过浏览器访问 能正常访问
//        - VUE：基于 Vue3 构建的纯前端应用，不依赖后端服务。      编译 运行 =》 通过浏览器访问 能正常访问
//        - VUE_SPRING：完整的前后端分离应用，前端基于 Vue3 架构，后端采用 SpringBoot 框架。   分别编译运行前后端工程   =》 通过浏览器访问 能正常访问
        String outPut = generateAppWithAI(systemPrompt, userPrompt, appId);
        ModelOutputParser.ParsedResult parsedResult = ModelOutputParser.parse(outPut);
        //update app set app_type = 实际的应用的类型 where id = 实际的应用id
        appMapper.update(new LambdaUpdateWrapper<App>()
                .eq(App::getId, appId)
                .set(App::getAppType, AppType.getValue(parsedResult.getAppType())));
        //本地存储   为后续的编译打包 做准备
        try {
            Path appPath = GeneratedAppWriter.writeFiles(appId, parsedResult.getFiles());
            //todo: 代码提交至gitee 第八章完成
            giteeService.commit(appId, appPath, parsedResult.getAppType(), parsedResult.getFiles());
            //1. 预览容器中Nginx配置  2. 预览url 的确认 & 修改 3.提取公共方法（优化代码）  4. 为实现方法的补充实现    done
            handelApp(appId, parsedResult.getAppType(), appPath, PreviewDeployPath.PREVIEW.getPath());
            String previewUrl = previewHost + appId + "/#/";
            appMapper.update(new LambdaUpdateWrapper<App>()
                    .eq(App::getId, appId)
                    .set(App::getPreviewUrl, previewUrl));
            return new GenerateAppResDTO(appId, previewUrl, parsedResult.getAppType());
        } catch (IOException e) {
            throw new ServiceException("应用代码写入异常：" + e.getMessage());
        }
    }

    //1. pullUserAppCode方法具体实现需要补充   2. 收集应用的代码文件方法具体实现需要补充 3. 系统提示词 & 用户提示词 补充
    @Override
    public GenerateAppResDTO appEdit(AppEditDTO appEditDTO) {
        Long appId = appEditDTO.getAppId();
        App app = appMapper.selectById(appId);
        if (app == null) {
            throw new ServiceException("应用不存在");
        }
        String userDir = System.getProperty("user.dir");
        Path userCodeBaseDir = Paths.get(userDir, "user-code/" + appId).toAbsolutePath();
        try {
            if (!Files.exists(userCodeBaseDir) || !Files.isDirectory(userCodeBaseDir) || Files.list(userCodeBaseDir).findAny().isEmpty()) {
                //从码云拉取代码
                giteeService.pullUserAppCode(appId, userCodeBaseDir);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Map<String, String> codeFiles = readCodeFiles(userCodeBaseDir);
        String systemPrompt = buildSystemPrompt(appEditDTO.getElementSelector(), appEditDTO.getNewContent());
        String userPrompt = buildUserPrompt(codeFiles, appEditDTO.getElementSelector(), appEditDTO.getNewContent());
        String outPut = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, String.valueOf(appId)))
                .advisors(QuestionAnswerAdvisor
                        .builder(vectorStore)
                        .searchRequest(SearchRequest.builder().build())
                        .build()
                )
                .call()
                .content();
        ModelOutputParser.ParsedResult parsedResult = ModelOutputParser.parse(outPut);
        //update app set app_type = 实际的应用的类型 where id = 实际的应用id
        appMapper.update(new LambdaUpdateWrapper<App>()
                .eq(App::getId, appId)
                .set(App::getAppType, AppType.getValue(parsedResult.getAppType())));
        //本地存储   为后续的编译打包 做准备
        try {
            Path appPath = GeneratedAppWriter.writeFiles(appId, parsedResult.getFiles());
            giteeService.delete(appId);
            giteeService.commit(appId, appPath, parsedResult.getAppType(), parsedResult.getFiles());
            //1. 预览容器中Nginx配置  2. 预览url 的确认 & 修改 3.提取公共方法（优化代码）  4. 为实现方法的补充实现    done
            handelApp(appId, parsedResult.getAppType(), appPath, PreviewDeployPath.PREVIEW.getPath());
            return new GenerateAppResDTO(appId, app.getPreviewUrl(), parsedResult.getAppType());
        } catch (IOException e) {
            throw new ServiceException("应用代码写入异常：" + e.getMessage());
        }
    }

    @Override
    public String vsCodeEdit(Long appId) {
        App app = appMapper.selectById(appId);
        if (app == null) {
            throw new ServiceException("应用不存在");
        }
        String userDir = System.getProperty("user.dir");
        Path userCodeBaseDir = Paths.get(userDir, "user-code/" + appId).toAbsolutePath();
        try {
            if (!Files.exists(userCodeBaseDir) || !Files.isDirectory(userCodeBaseDir) || Files.list(userCodeBaseDir).findAny().isEmpty()) {
                //从码云拉取代码
                giteeService.pullUserAppCode(appId, userCodeBaseDir);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return vscodeHost + ":" + vscodePort + "/?folder=/home/workspace/" + appId;
    }

    @Override
    public Boolean completeEdit(Long appId) {
        App app = appMapper.selectById(appId);
        if (app == null) {
            throw new ServiceException("应用不存在");
        }
        try {
            // 本地存储  ok     码云仓库  ok
            String userDir = System.getProperty("user.dir");
            Path userCodeBaseDir = Paths.get(userDir, "user-code/" + appId).toAbsolutePath();
            Map<String, String> codeFiles = readCodeFiles(userCodeBaseDir);

            String appType = AppType.getAppType(app.getAppType());
            giteeService.delete(appId);
            giteeService.commit(appId, userCodeBaseDir, appType, codeFiles);
            handelApp(appId, appType, userCodeBaseDir, PreviewDeployPath.PREVIEW.getPath());
        } catch (Exception e) {
            throw new ServiceException(e.getMessage());
        }
        return true;
    }

    @Override
    public String deployApp(Long appId) {
        App app = appMapper.selectById(appId);
        if (app == null) {
            throw new ServiceException("应用不存在");
        }
        if (Objects.equals(DeployStatus.DEPLOYED.getValue(), app.getDeployStatus())) {
            return app.getAppUrl();
        }
        String userDir = System.getProperty("user.dir");
        Path userCodeBaseDir = Paths.get(userDir, "user-code/" + appId).toAbsolutePath();
        try {
            if (!Files.exists(userCodeBaseDir) || !Files.isDirectory(userCodeBaseDir) || Files.list(userCodeBaseDir).findAny().isEmpty()) {
                //从码云拉取代码
                giteeService.pullUserAppCode(appId, userCodeBaseDir);
            }
            String appType = AppType.getAppType(app.getAppType());
            int containerPort = createContainer(appId);
            handelApp(appId, appType, userCodeBaseDir, PreviewDeployPath.DEPLOY.getPath());
            String appUrl = deployHost + ":" + containerPort + "/deploy/" + appId + "/#/";
            app.setAppUrl(appUrl);
            app.setDeployStatus(DeployStatus.DEPLOYED.getValue());
            appMapper.updateById(app);
            return appUrl;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private int createContainer(Long appId) {
        String containerName = deployContainerName + "-" + appId;
        int port = generatePort(appId);
        DockerBindConfig dockerBindConfig = createDockerBindConfig(appId);
        ContainerCreationResult result = createContainerWithRetry(appId, imageName, containerName, port, dockerBindConfig);
        if (!result.isSuccess()) {
            throw new ServiceException(
                    String.format("无法创建容器：无可用端口，appId=%d", appId),
                    ResultCode.ERROR.getCode());
        }
        return result.getPort();
    }

    private ContainerCreationResult createContainerWithRetry(Long appId, String imageName, String containerName,
                                                             int initialPort, DockerBindConfig bindConfig) {
        Exception lastException = null;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String containerId = createAndStartDockerContainer(
                        imageName, containerName, initialPort, bindConfig);

                return ContainerCreationResult.success(containerId, initialPort);

            } catch (Exception e) {
                if (isPortConflictError(e)) {
                    log.warn("端口 {} 被占用，appId={}, 尝试 {}/{}",
                            initialPort, appId, attempt + 1, maxRetries);
                    lastException = e;
                    initialPort = calculateNextPort(initialPort);
                } else {
                    // 非端口冲突错误，直接抛出
                    log.error("创建容器失败，appId={}, 端口={}", appId, initialPort, e);
                    throw new ServiceException(
                            "创建容器失败: " + e.getMessage(),
                            ResultCode.ERROR.getCode());
                }
            }
        }

        log.error("端口重试耗尽，appId={}, 尝试次数: {}", appId, maxRetries);
        return ContainerCreationResult.failure(lastException);
    }

    private String createAndStartDockerContainer(String imageName, String containerName,
                                                 int port, DockerBindConfig bindConfig) {
        Ports portBindings = new Ports();
        portBindings.bind(bindConfig.getNginxPort(), Ports.Binding.bindPort(port));

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withPortBindings(portBindings)
                .withBinds(bindConfig.getDeployBind(), bindConfig.getNginxConfBind())
                .withRestartPolicy(RestartPolicy.unlessStoppedRestart());

        CreateContainerResponse created = dockerClient.createContainerCmd(imageName)
                .withName(containerName)
                .withHostConfig(hostConfig)
                .withExposedPorts(bindConfig.getNginxPort())
                .exec();

        String containerId = created.getId();
        dockerClient.startContainerCmd(containerId).exec();

        return containerId;
    }

    private boolean isPortConflictError(Exception e) {
        String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return errorMsg.contains("port")
                || errorMsg.contains("bind")
                || errorMsg.contains("already in use")
                || errorMsg.contains("address already in use")
                || e instanceof ConflictException;
    }


    private static int generatePort(Long appId) {
        int port = 5000 + (int) ((appId * 3001L) % 3000);
        log.debug("为 appId {} 生成端口: {}", appId, port);
        return port;
    }

    //5001  5002    5000 ~ 8000
    private int calculateNextPort(int currentPort) {
        return 5000 + ((currentPort - 5000 + 1) % 3000);
    }

    private DockerBindConfig createDockerBindConfig(Long appId) {
        String appDeployDir = Paths.get(deployPath, appId.toString()).toString();
        Volume deployVolume = new Volume("/workspace/user-deploy/" + appId);
        Bind deployBind = new Bind(appDeployDir.toString(), deployVolume, AccessMode.rw);

        Volume nginxConfVolume = new Volume("/etc/nginx/nginx.conf");
        Bind nginxConfBind = new Bind(nginxPath, nginxConfVolume, AccessMode.rw);
        ExposedPort nginxPort = ExposedPort.tcp(80);
        return new DockerBindConfig(deployBind, nginxConfBind, nginxPort);
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

    private String generateAppWithAI(String systemPrompt, String userPrompt, Long appId) {
        String conversationId = String.valueOf(appId);
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(QuestionAnswerAdvisor
                        .builder(vectorStore)
                        .searchRequest(SearchRequest.builder().build())
                        .build()
                )
                .call()
                .content();
    }

    private void handelApp(Long appId, String appType, Path appPath, String previewDeployPath) throws IOException {
        switch (appType) {
            case "HTML":
                Path targetPath = FileUtil.ensureAppDir(appId, previewDeployPath).resolve("dist");
                if (!Files.exists(targetPath)) {
                    Files.createDirectory(targetPath);
                }
                Path indexFile = appPath.resolve("index.html");
                Path targetFile = targetPath.resolve("index.html");
                Files.copy(indexFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                break;
            case "VUE":
                AppBuildUtil.buildVuePro(appId, appPath, previewDeployPath);
                break;
            case "VUE_SPRING":
                Path vueProPath = appPath.resolve("frontend");
                AppBuildUtil.buildVuePro(appId, vueProPath, previewDeployPath);
                Path springProPath = appPath.resolve("backend");
                AppBuildUtil.buildSpringBoot(appId, springProPath, dockerClient, previewDeployPath, containerName);
                break;
            default:
                throw new ServiceException("不支持的应用类型：" + appType);
        }
    }

    private Map<String, String> readCodeFiles(Path userCodeBaseDir) {
        Map<String, String> files = new LinkedHashMap<>();

        try (Stream<Path> paths = Files.walk(userCodeBaseDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> isCodeFile(p))
                    .forEach(p -> {
                        try {
                            String relativePath = userCodeBaseDir.relativize(p).toString().replace('\\', '/');
                            String content = Files.readString(p, StandardCharsets.UTF_8);
                            files.put(relativePath, content);
                        } catch (IOException e) {
                            log.warn("读取文件失败: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return files;
    }

    private boolean isCodeFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".html") ||
                fileName.endsWith(".vue") ||
                fileName.endsWith(".js") ||
                fileName.endsWith(".ts") ||
                fileName.endsWith(".css") ||
                fileName.endsWith(".java") ||
                fileName.endsWith(".json") ||
                fileName.endsWith(".xml");
    }

    private String buildSystemPrompt(String elementSelector, String newContent) {
        return String.join("\n",
                "你是一个“精确 DOM 定点修改器”，不是代码重构器。",
                "你的唯一任务：只修改一个已经被精确定位的 DOM 元素，其余任何内容都禁止改动。",
                "",
                "====================",
                "【目标元素（已由系统精确定位）】",
                "CSS 选择器：",
                elementSelector,
                "",
                "该选择器唯一且只指向一个确定的 DOM 元素。",
                "你【不需要】也【不允许】重新理解、简化或泛化该选择器。",
                "",
                "====================",
                "【硬性修改规则（不可违反）】",
                "1. 只允许修改该选择器命中的“单一元素”。",
                "2. 禁止修改任何兄弟元素、父元素或子元素。",
                "3. 禁止新增、删除、重排任何 DOM 结构。",
                "4. 禁止修改任何全局 CSS、公共 class 或选择器。",
                "",
                "====================",
                "【nth-child 特别规则（最高优先级）】",
                "当前选择器包含 :nth-child(n)，这表示：",
                "- 只允许影响该列表中的第 n 个元素；",
                "- 其他列表项必须在结构、样式、文本上保持 100% 不变。",
                "",
                "当修改涉及颜色、字体、背景、样式时：",
                "✓ 强制优先使用该元素的内联 style；",
                "✓ 或仅对该完整选择器生效的样式；",
                "✗ 绝对禁止修改公共 class（例如 .teacher-name）。",
                "",
                "====================",
                "【修改指令】",
                newContent,
                "",
                "====================",
                "【输出要求】",
                "- 只输出被修改的文件；",
                "- 文件内容必须是修改后的完整内容；",
                "- 除目标元素外，其他任何字符都不得变化；",
                "- 不要输出解释、注释或多余文本。",
                "- 文件内容**: 紧接着按以下格式输出每个文件：",
                "FILE: <relative_path>",
                "```<language>",
                "<complete_file_content>",
                "```",
                "   - `<relative_path>`: 文件的相对路径，路径中省略appId（如 `index.html`, `frontend/src/App.vue`, `backend/src/main/resources/application.properties`）。",
                "   - `<complete_file_content>`: **完整**的文件内容，**绝对禁止**省略、使用占位符或 `// ...`。"
        );
    }

    private String buildUserPrompt(Map<String, String> codeFiles, String elementSelector, String newContent) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("【精确修改请求】\n\n");

        prompt.append("目标 CSS 选择器（已精确定位，无需分析）：\n");
        prompt.append(elementSelector).append("\n\n");

        prompt.append("修改类型：样式修改（仅作用于该元素）\n");
        prompt.append("具体要求：").append(newContent).append("\n\n");

        if (elementSelector.contains(":nth-child(")) {
            prompt.append("⚠️ 注意：该选择器包含 nth-child，表示这是列表中的一个单独元素。\n");
            prompt.append("你必须确保其他列表项在任何方面都不发生变化。\n\n");
        }

        prompt.append("以下是完整代码文件，请只对目标元素进行最小必要修改：\n\n");

        for (Map.Entry<String, String> entry : codeFiles.entrySet()) {
            prompt.append("FILE: ").append(entry.getKey()).append("\n");
            prompt.append("```html\n");
            prompt.append(entry.getValue());
            prompt.append("\n```\n\n");
        }

        return prompt.toString();
    }


}
