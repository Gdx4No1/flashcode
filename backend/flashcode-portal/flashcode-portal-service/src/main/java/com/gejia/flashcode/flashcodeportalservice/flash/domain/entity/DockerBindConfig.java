package com.gejia.flashcode.flashcodeportalservice.flash.domain.entity;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DockerBindConfig {
    private final Bind deployBind;
    private final Bind nginxConfBind;
    private final ExposedPort nginxPort;

    public DockerBindConfig(Bind deployBind, Bind nginxConfBind, ExposedPort nginxPort) {
        this.deployBind = deployBind;
        this.nginxConfBind = nginxConfBind;
        this.nginxPort = nginxPort;
    }

    public Bind getDeployBind() {
        return deployBind;
    }

    public Bind getNginxConfBind() {
        return nginxConfBind;
    }

    public ExposedPort getNginxPort() {
        return nginxPort;
    }
}
