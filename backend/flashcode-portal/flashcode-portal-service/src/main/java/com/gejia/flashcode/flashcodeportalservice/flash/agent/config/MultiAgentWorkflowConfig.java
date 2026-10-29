package com.gejia.flashcode.flashcodeportalservice.flash.agent.config;

import com.gejia.flashcode.flashcodefileapi.feign.FileFeignClient;
import com.gejia.flashcode.flashcodeportalservice.flash.agent.MultiAgentWorkflow;
import com.gejia.flashcode.flashcodeportalservice.flash.mapper.AppMapper;
import com.gejia.flashcode.flashcodeportalservice.flash.service.IGiteeService;
import com.github.dockerjava.api.DockerClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MultiAgentWorkflowConfig {

    @Value("${app.preview.host}")
    private String previewHost;

    @Value("${app.preview.container-name}")
    private String containerName;


    @Bean
    public MultiAgentWorkflow multiAgentWorkflow(AppMapper appMapper, ChatClient chatClient,
                                                 VectorStore vectorStore, DockerClient dockerClient,
                                                 FileFeignClient fileFeignClient, IGiteeService giteeService) {
        return new MultiAgentWorkflow(
                appMapper,
                chatClient,
                vectorStore,
                dockerClient,
                containerName,
                previewHost,
                fileFeignClient,
                giteeService);
    }
}
