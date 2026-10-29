package com.gejia.flashcode.flashcodeportalservice.flash.utils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectExecCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PreviewBackendLauncherTest {
    private DockerClient docker(long exitCode, String output) {
        DockerClient docker = mock(DockerClient.class);
        ExecCreateCmd create = mock(ExecCreateCmd.class, RETURNS_SELF);
        ExecCreateCmdResponse created = mock(ExecCreateCmdResponse.class);
        when(created.getId()).thenReturn("test-exec");
        when(create.exec()).thenReturn(created);
        when(docker.execCreateCmd("preview")).thenReturn(create);
        ExecStartCmd start = mock(ExecStartCmd.class);
        when(docker.execStartCmd("test-exec")).thenReturn(start);
        doAnswer(invocation -> {
            ResultCallback<Frame> callback = invocation.getArgument(0);
            callback.onNext(new Frame(StreamType.STDOUT, output.getBytes(StandardCharsets.UTF_8)));
            callback.onComplete();
            return callback;
        }).when(start).exec(any());
        InspectExecCmd inspect = mock(InspectExecCmd.class);
        InspectExecResponse result = mock(InspectExecResponse.class);
        when(result.getExitCodeLong()).thenReturn(exitCode);
        when(inspect.exec()).thenReturn(result);
        when(docker.inspectExecCmd("test-exec")).thenReturn(inspect);
        return docker;
    }

    @Test void acceptsOnlyConfirmedSuccessfulStartup() {
        assertDoesNotThrow(() -> PreviewBackendLauncher.launch(docker(0, "BACKEND_READY"), "preview", 42L, "/var/www/html"));
    }

    @Test void reportsMissingNginxConfiguration() {
        var error = assertThrows(IOException.class, () -> PreviewBackendLauncher.launch(
                docker(41, "预览 Nginx 尚未包含 flashcode-locations 配置"), "preview", 42L, "/var/www/html"));
        assertTrue(error.getMessage().contains("Nginx"));
    }

    @Test void doesNotTreatNormalExitWithoutReadinessAsSuccess() {
        assertThrows(IOException.class, () -> PreviewBackendLauncher.launch(docker(0, "starting"), "preview", 42L, "/var/www/html"));
    }

    @Test void rejectsUnsafePathsBeforeCallingDocker() {
        DockerClient docker = mock(DockerClient.class);
        assertThrows(IllegalArgumentException.class,
                () -> PreviewBackendLauncher.launch(docker, "preview", 42L, "/tmp/../etc"));
        verifyNoInteractions(docker);
    }
}
