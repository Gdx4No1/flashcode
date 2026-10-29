package com.gejia.flashcode.flashcodeportalservice.flash.eums;

import lombok.Getter;

@Getter
public enum PreviewDeployPath {

    PREVIEW("user-preview", "预览地址"),

    DEPLOY("user-deploy", "部署地址");

    PreviewDeployPath(String path, String desc) {
        this.path = path;
        this.desc = desc;
    }

    private String path;

    private String desc;
}
