package com.gejia.flashcode.flashcodeportalservice.flash.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.entity.App;
import com.gejia.flashcode.flashcodeportalservice.flash.mapper.AppMapper;
import com.gejia.flashcode.flashcodeportalservice.flash.service.impl.AppGenerationServiceImpl;
import com.gejia.flashcode.flashcodeportalservice.user.entity.dto.UserDTO;
import com.gejia.flashcode.flashcodeportalservice.user.service.IUserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class HtmlGenerationFlowTest {
    @TempDir Path directory;

    @Test
    @ResourceLock("user.dir")
    void generatesSourceAndPublishesHtmlToConfiguredPreviewDirectory() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "html-test"), App.class);
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("""
                APP_TYPE=HTML
                FILE: index.html
                ```html
                <!doctype html><html lang="zh-CN"><body>葛佳的应用</body></html>
                ```
                """)))));
        AppMapper mapper = mock(AppMapper.class);
        App app = new App(); app.setId(42L); app.setUserId(27L);
        when(mapper.selectById(42L)).thenReturn(app);
        when(mapper.update(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        IUserService users = mock(IUserService.class);
        UserDTO user = new UserDTO(); user.setUserId(27L);
        when(users.getLoginUser()).thenReturn(user);
        var service = new AppGenerationServiceImpl(ChatClient.builder(model).build());
        ReflectionTestUtils.setField(service, "appMapper", mapper);
        ReflectionTestUtils.setField(service, "userService", users);
        ReflectionTestUtils.setField(service, "previewHost", "https://preview.example.test/");
        ReflectionTestUtils.setField(service, "previewLocalRoot", directory.resolve("published").toString());
        String previous = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", directory.toString());
            var result = service.appGenerate(42L, "生成个人介绍页");
            assertEquals(0, result.getAppType());
            assertEquals("https://preview.example.test/42/", result.getPreviewUrl());
            Path code = directory.resolve("user-code/42/index.html");
            Path preview = directory.resolve("published/42/index.html");
            assertTrue(Files.readString(code).contains("葛佳的应用"));
            assertEquals(Files.readString(code), Files.readString(preview));
            verify(mapper, times(1)).update(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        } finally {
            System.setProperty("user.dir", previous);
        }
    }
}
