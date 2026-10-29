package com.gejia.flashcode.flashcodeportalservice.flash.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Personal OpenAI-compatible model; credentials come from private runtime configuration. */
@Configuration
public class PersonalModelConfig {
    @Bean("flashcodeChatModel")
    public ChatModel flashcodeChatModel(
            @Value("${flashcode.ai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${flashcode.ai.api-key:${DEEPSEEK_API_KEY:}}") String apiKey,
            @Value("${flashcode.ai.model:deepseek-flash}") String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Set the model key in private configuration or DEEPSEEK_API_KEY");
        }
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build())
                .defaultOptions(OpenAiChatOptions.builder().model(model).topP(0.7).build())
                .build();
    }
}
