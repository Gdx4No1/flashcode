package com.gejia.flashcode.flashcodegiteemcpserver.config;

import com.gejia.flashcode.flashcodegiteemcpserver.service.GiteeService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GiteeMCPServerConfig {

    @Bean
    public ToolCallbackProvider giteeTools(GiteeService giteeService) {
        return MethodToolCallbackProvider.builder().toolObjects(giteeService).build();
    }
}
