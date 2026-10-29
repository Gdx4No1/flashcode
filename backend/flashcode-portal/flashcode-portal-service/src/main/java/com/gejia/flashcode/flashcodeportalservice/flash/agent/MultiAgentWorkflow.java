package com.gejia.flashcode.flashcodeportalservice.flash.agent;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.gejia.flashcode.flashcodecommondomain.exception.ServiceException;
import com.gejia.flashcode.flashcodefileapi.feign.FileFeignClient;
import com.gejia.flashcode.flashcodeportalservice.flash.agent.node.*;
import com.gejia.flashcode.flashcodeportalservice.flash.mapper.AppMapper;
import com.gejia.flashcode.flashcodeportalservice.flash.service.IGiteeService;
import com.github.dockerjava.api.DockerClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

public class MultiAgentWorkflow {

    private StateGraph stateGraph;

    private AppMapper appMapper;

    private ChatClient chatClient;

    private VectorStore vectorStore;

    private DockerClient dockerClient;

    private String containerName;

    private String previewHost;

    private FileFeignClient fileFeignClient;

    private IGiteeService giteeService;

    private static final String NODE_APP_GENERATION = "appGeneration";

    private static final String NODE_BUILD_PREVIEW = "buildPreview";

    private static final String NODE_ERROR_FIX = "errorFix";

    private static final String NODE_APP_SCREENSHOT = "appScreenshot";

    private static final String NODE_CODE_COMMIT = "codeCommit";

    public MultiAgentWorkflow(AppMapper appMapper, ChatClient chatClient,
                              VectorStore vectorStore, DockerClient dockerClient, String containerName,
                              String previewHost, FileFeignClient fileFeignClient, IGiteeService giteeService) {
        this.stateGraph = new StateGraph(createKeyStrategyFactory());
        this.appMapper = appMapper;
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.dockerClient = dockerClient;
        this.containerName = containerName;
        this.previewHost = previewHost;
        this.fileFeignClient = fileFeignClient;
        this.giteeService = giteeService;
        addNode();
        addEdge();
    }

    private void addNode() {
        try {
            stateGraph.addNode(NODE_APP_GENERATION, node_async(new AppGenerationAgent(appMapper, chatClient, vectorStore)));
            stateGraph.addNode(NODE_BUILD_PREVIEW, node_async(new BuildPreviewAgent(dockerClient, containerName, previewHost, appMapper)));
            stateGraph.addNode(NODE_ERROR_FIX, node_async(new ErrorFixAgent(chatClient, vectorStore)));
            stateGraph.addNode(NODE_APP_SCREENSHOT, node_async(new AppScreenshotAgent(fileFeignClient, appMapper)));
            stateGraph.addNode(NODE_CODE_COMMIT, node_async(new CodeCommitAgent(giteeService)));
        } catch (GraphStateException e) {
            throw new RuntimeException(e);
        }
    }

    private void addEdge() {
        try {
            stateGraph.addEdge(StateGraph.START, NODE_APP_GENERATION);
            stateGraph.addEdge(NODE_APP_GENERATION, NODE_BUILD_PREVIEW);
            stateGraph.addConditionalEdges(NODE_BUILD_PREVIEW, state -> {
                Boolean buildPreview = state.value("buildPreview", Boolean.class).orElse(null);
                if (buildPreview) {
                    return CompletableFuture.completedFuture("SUCCESS");
                }
                return  CompletableFuture.completedFuture("ERROR");
            }, Map.of("SUCCESS", NODE_APP_SCREENSHOT,
                    "ERROR", NODE_ERROR_FIX));

            stateGraph.addConditionalEdges(NODE_ERROR_FIX, state -> {
                Boolean fixSuccess = state.value("fixSuccess", Boolean.class).orElse(null);
                if (fixSuccess) {
                    return CompletableFuture.completedFuture("SUCCESS");
                }
                return  CompletableFuture.completedFuture("ERROR");
            }, Map.of("SUCCESS", NODE_BUILD_PREVIEW,
                    "ERROR", StateGraph.END));

            stateGraph.addEdge(NODE_APP_SCREENSHOT, NODE_CODE_COMMIT);
            stateGraph.addEdge(NODE_CODE_COMMIT, StateGraph.END);
        } catch (GraphStateException e) {
            throw new RuntimeException(e);
        }
    }

    //执行工作流
    public OverAllState execute(OverAllState overAllState) {
        try {
            CompiledGraph compiledGraph = stateGraph.compile();
            RunnableConfig config = RunnableConfig.builder().build();
            overAllState.registerKeyAndStrategy(createKeyStrategies());
            return compiledGraph.invoke(overAllState, config).orElseThrow(() -> new ServiceException("工作流执行结果为空"));
        } catch (GraphStateException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, KeyStrategy> createKeyStrategies() {
        Map<String, KeyStrategy> keyStrategies = new HashMap<>();

        // 输入参数
        keyStrategies.put("appId", new ReplaceStrategy());
        keyStrategies.put("appDoc", new ReplaceStrategy());
        keyStrategies.put("sketch", new ReplaceStrategy());
        keyStrategies.put("contentType", new ReplaceStrategy());

        // AppGenerationAgent 输出
        keyStrategies.put("appGenerated", new ReplaceStrategy());
        keyStrategies.put("appPath", new ReplaceStrategy());
        keyStrategies.put("appType", new ReplaceStrategy());
        keyStrategies.put("files", new ReplaceStrategy());

        // BuildPreviewAgent 输出
        keyStrategies.put("buildPreview", new ReplaceStrategy());
        keyStrategies.put("previewUrl", new ReplaceStrategy());
        keyStrategies.put("errorType", new ReplaceStrategy());

        //ErrorFixAgent 输出
        keyStrategies.put("fixSuccess", new ReplaceStrategy());

        // AppScreenshotAgent 输出
        keyStrategies.put("appScreenshot", new ReplaceStrategy());

        // CodeCommitAgent 输出
        keyStrategies.put("codeCommit", new ReplaceStrategy());

        // 通用状态键
        keyStrategies.put("status", new ReplaceStrategy());
        keyStrategies.put("error", new AppendStrategy());

        return keyStrategies;
    }

    public static KeyStrategyFactory createKeyStrategyFactory() {
        Map<String, KeyStrategy> strategies = createKeyStrategies();
        return new KeyStrategyFactoryBuilder().addStrategies(strategies).build();
    }
}
