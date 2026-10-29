package com.gejia.flashcode.flashcodeportalservice.flash.service.impl;

import com.gejia.flashcode.flashcodecommondomain.exception.ServiceException;
import com.gejia.flashcode.flashcodeportalservice.user.entity.dto.UserDTO;
import com.gejia.flashcode.flashcodeportalservice.user.service.IUserService;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 只验证配置绑定和工具提示参数；所有模型、用户服务都使用桩，不触及远程仓库。 */
class GiteeServiceImplTest {
    @Test void canonicalConfigurationReachesTool() {
        checkPrompt(Map.of("gitee.repository.owner", "gejia", "gitee.repository.name", "my-apps",
                "gitee.repository.branch", "develop"), "gejia", "my-apps", "develop");
    }

    @Test void legacyConfigurationStillWorksWithRealBootstrapDefaults() {
        checkPrompt(Map.of("gitee.user-code.owner", "legacy-owner", "gitee.user-code.rpo", "legacy-repo",
                "gitee.user-code.branch", "legacy-branch"), "legacy-owner", "legacy-repo", "legacy-branch");
    }

    @Test void canonicalConfigurationWinsOverLegacy() {
        checkPrompt(Map.of("gitee.repository.owner", "new-owner", "gitee.repository.name", "new-repo",
                "gitee.repository.branch", "new-branch", "gitee.user-code.owner", "old-owner",
                "gitee.user-code.rpo", "old-repo", "gitee.user-code.branch", "old-branch"),
                "new-owner", "new-repo", "new-branch");
    }

    @Test void blankCanonicalValuesFallBackToLegacy() {
        checkPrompt(Map.of("gitee.repository.owner", " ", "gitee.repository.name", "",
                "gitee.repository.branch", " ", "gitee.user-code.owner", "owner",
                "gitee.user-code.rpo", "repo", "gitee.user-code.branch", "topic"), "owner", "repo", "topic");
    }

    @Test void environmentStyleConfigurationAndDefaultBranchWork() {
        checkPrompt(Map.of("GITEE_REPOSITORY_OWNER", " personal ", "GITEE_REPOSITORY_NAME", " apps "),
                "personal", "apps", "main");
    }

    @Test void missingRepositoryFailsBeforeCallingModel() {
        try (var fixture = fixture(Map.of())) {
            assertThrows(ServiceException.class, () -> fixture.service().pullUserAppCode(42L, Path.of("D:/test-only")));
            verifyNoInteractions(fixture.client);
        }
    }

    @Test void disabledIntegrationFailsBeforeCallingModel() {
        try (var fixture = fixture(Map.of("gitee.enabled", false))) {
            assertFalse(fixture.service().enabled());
            assertThrows(ServiceException.class, () -> fixture.service().pullUserAppCode(42L, Path.of("D:/test-only")));
            verifyNoInteractions(fixture.client);
        }
    }

    private void checkPrompt(Map<String, Object> properties, String owner, String repo, String branch) {
        try (var fixture = fixture(properties)) {
            var request = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
            var response = mock(ChatClient.CallResponseSpec.class);
            when(fixture.client.prompt()).thenReturn(request);
            when(request.call()).thenReturn(response);
            when(response.content()).thenReturn("test-only");
            fixture.service().pullUserAppCode(42L, Path.of("D:/test-only"));
            verify(request).user(contains("owner=" + owner + ", repo=" + repo + ", branch=" + branch + ", appId=42,"));
            verify(request).call();
        }
    }

    private Fixture fixture(Map<String, Object> properties) {
        var context = new AnnotationConfigApplicationContext();
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("bootstrap.yml"));
        context.getEnvironment().getPropertySources().addFirst(new PropertiesPropertySource("bootstrap", yaml.getObject()));
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("gitee.enabled", true);
        overrides.putAll(properties);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", overrides));
        var client = mock(ChatClient.class);
        var users = mock(IUserService.class);
        var user = new UserDTO();
        user.setUserId(7L);
        when(users.getLoginUser()).thenReturn(user);
        context.registerBean(ChatClient.class, () -> client);
        context.registerBean(IUserService.class, () -> users);
        context.register(GiteeServiceImpl.class);
        context.refresh();
        return new Fixture(context, client);
    }

    private record Fixture(AnnotationConfigApplicationContext context, ChatClient client) implements AutoCloseable {
        GiteeServiceImpl service() { return context.getBean(GiteeServiceImpl.class); }
        public void close() { context.close(); }
    }
}
