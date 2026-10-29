package com.gejia.flashcode.flashcodeportalservice.flash.config;

import com.gejia.flashcode.flashcodeportalservice.flash.advisor.TokenUsageAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ChatClientConfig {

    @Bean("chatClient")
    public ChatClient chatClient(@Qualifier("flashcodeChatModel") ChatModel chatModel,
                                 RedisDbChatMemoryConfig chatMemory,
                                 List<ToolCallbackProvider> toolCallbackProviders,
                                 TokenUsageAdvisor tokenUsageAdvisor) {
        MessageChatMemoryAdvisor chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        ChatClient.Builder builder = ChatClient.builder(chatModel)
                .defaultAdvisors(chatMemoryAdvisor, new SimpleLoggerAdvisor(), tokenUsageAdvisor)
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .topP(0.7)
                                .build()
                );
        for (ToolCallbackProvider toolCallbackProvider : toolCallbackProviders) {
            builder.defaultToolCallbacks(toolCallbackProvider);
        }
        return builder.build();
    }
}
