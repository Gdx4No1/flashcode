package com.gejia.flashcode.flashcodeportalservice.flash.domain.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ContainerCreationResult {

    private final boolean success;
    private final String containerId;
    private final int port;
    private final Exception exception;

    private ContainerCreationResult(boolean success, String containerId, int port, Exception exception) {
        this.success = success;
        this.containerId = containerId;
        this.port = port;
        this.exception = exception;
    }

    public static ContainerCreationResult success(String containerId, int port) {
        return new ContainerCreationResult(true, containerId, port, null);
    }

    public static ContainerCreationResult failure(Exception exception) {
        return new ContainerCreationResult(false, null, -1, exception);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getContainerId() {
        return containerId;
    }

    public int getPort() {
        return port;
    }

    public Exception getException() {
        return exception;
    }
}
