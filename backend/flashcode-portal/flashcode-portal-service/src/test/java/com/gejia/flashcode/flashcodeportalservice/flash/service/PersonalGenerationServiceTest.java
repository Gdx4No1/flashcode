package com.gejia.flashcode.flashcodeportalservice.flash.service;

import com.gejia.flashcode.flashcodeportalservice.flash.domain.entity.App;
import com.gejia.flashcode.flashcodeportalservice.flash.mapper.AppMapper;
import com.gejia.flashcode.flashcodeportalservice.flash.service.impl.AppGenerationServiceImpl;
import com.gejia.flashcode.flashcodeportalservice.flash.service.impl.RequirementsServiceImpl;
import com.gejia.flashcode.flashcodeportalservice.user.entity.dto.UserDTO;
import com.gejia.flashcode.flashcodeportalservice.user.service.IUserService;
import com.gejia.flashcode.flashcodecommondomain.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 只验证本人的服务实现；数据库、用户服务与模型均使用隔离替身。 */
class PersonalGenerationServiceTest {
    AppMapper mapper;
    IUserService users;
    ChatModel model;
    RequirementsServiceImpl requirements;
    AppGenerationServiceImpl generation;

    @BeforeEach void setup() {
        mapper = mock(AppMapper.class);
        users = mock(IUserService.class);
        model = mock(ChatModel.class);
        ChatClient client = ChatClient.builder(model).build();
        requirements = new RequirementsServiceImpl();
        ReflectionTestUtils.setField(requirements, "appMapper", mapper);
        ReflectionTestUtils.setField(requirements, "userService", users);
        ReflectionTestUtils.setField(requirements, "chatClient", client);
        generation = new AppGenerationServiceImpl(client);
        ReflectionTestUtils.setField(generation, "appMapper", mapper);
        ReflectionTestUtils.setField(generation, "userService", users);
    }

    private void login() {
        UserDTO user = new UserDTO();
        user.setUserId(27L);
        when(users.getLoginUser()).thenReturn(user);
    }

    private void response(String content) {
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(content)))));
    }

    @Test void savesRequirementsForActualLoggedInUser() {
        login();
        response("# 应用需求文档\n## 1. 应用名称\n记账本\n## 2. 应用描述\n记录收支\n## 3. 应用核心功能\n添加记录");
        when(mapper.insert(any(App.class))).thenAnswer(invocation -> {
            App app = invocation.getArgument(0);
            assertEquals(27L, app.getUserId());
            assertEquals("记账本", app.getAppName());
            app.setId(42L);
            return 1;
        });
        assertEquals(42L, requirements.requirementsGenerate("做一个记账本").getAppId());
    }

    @Test void rejectsAnonymousRequirementsBeforeCallingModel() {
        assertThrows(ServiceException.class, () -> requirements.requirementsGenerate("记账本"));
        verify(model, never()).call(any(Prompt.class));
        verifyNoInteractions(mapper);
    }

    @Test void rejectsBlankInputBeforeCallingUserOrModel() {
        assertThrows(IllegalArgumentException.class, () -> requirements.requirementsGenerate(" "));
        verify(model, never()).call(any(Prompt.class));
        verifyNoInteractions(users, mapper);
    }

    @Test void doesNotInsertEmptyModelResponse() {
        login(); response("");
        assertThrows(ServiceException.class, () -> requirements.requirementsGenerate("记账本"));
        verifyNoInteractions(mapper);
    }

    @Test void rejectsIncompleteRequirements() {
        login(); response("只是一句普通回答");
        assertThrows(ServiceException.class, () -> requirements.requirementsGenerate("记账本"));
        verifyNoInteractions(mapper);
    }

    @Test void reportsDatabaseInsertFailure() {
        login(); response("## 1. 应用名称\n记账本\n## 2. 应用描述\n记录收支");
        assertThrows(ServiceException.class, () -> requirements.requirementsGenerate("记账本"));
    }

    @Test void rejectsOtherUsersApplicationBeforeCallingModel() {
        login();
        App app = new App(); app.setUserId(99L);
        when(mapper.selectById(42L)).thenReturn(app);
        assertThrows(ServiceException.class, () -> generation.appGenerate(42L, "生成页面"));
        verify(model, never()).call(any(Prompt.class));
        verify(mapper, never()).update(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test void rejectsMissingApplicationBeforeCallingModel() {
        login();
        assertThrows(ServiceException.class, () -> generation.appGenerate(42L, "生成页面"));
        verify(model, never()).call(any(Prompt.class));
    }

    @Test void rejectsIncompleteGeneratedCodeBeforeDatabaseUpdate() {
        login();
        App app = new App(); app.setUserId(27L);
        when(mapper.selectById(42L)).thenReturn(app);
        response("APP_TYPE=VUE3\nFILE: src/App.vue\n```vue\n<template>Hello</template>\n```");
        assertThrows(ServiceException.class, () -> generation.appGenerate(42L, "生成页面"));
        verify(mapper, never()).update(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test void rejectsLengthLimitedModelOutputBeforeWritingOrUpdating() {
        login();
        App app = new App(); app.setUserId(27L);
        when(mapper.selectById(42L)).thenReturn(app);
        Generation truncated = mock(Generation.class, RETURNS_DEEP_STUBS);
        when(truncated.getMetadata().getFinishReason()).thenReturn("length");
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(truncated)));
        var error = assertThrows(ServiceException.class, () -> generation.appGenerate(42L, "生成页面"));
        assertTrue(error.getMessage().contains("长度上限"));
        verify(mapper, never()).update(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test void reportsUnclosedSourceAsActionableFailure() {
        login();
        App app = new App(); app.setUserId(27L);
        when(mapper.selectById(42L)).thenReturn(app);
        response("APP_TYPE=VUE3\nFILE: src/App.vue\n```vue\n<style scoped>\n.title {");
        var error = assertThrows(ServiceException.class, () -> generation.appGenerate(42L, "生成页面"));
        assertTrue(error.getMessage().contains("不完整"));
        verify(mapper, never()).update(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }
}
