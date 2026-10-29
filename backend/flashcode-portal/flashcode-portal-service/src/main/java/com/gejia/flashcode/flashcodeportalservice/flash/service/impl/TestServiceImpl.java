package com.gejia.flashcode.flashcodeportalservice.flash.service.impl;

import com.gejia.flashcode.flashcodeportalservice.flash.service.ITestService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TestServiceImpl implements ITestService {

    @Autowired
    private ChatClient chatClient;

    @Override
    public String testChat(String prompt, String conversationId) {
        return chatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(prompt).call().content();
    }
}
