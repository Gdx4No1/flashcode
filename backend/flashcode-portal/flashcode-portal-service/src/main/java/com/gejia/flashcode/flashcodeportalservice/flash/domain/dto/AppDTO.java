package com.gejia.flashcode.flashcodeportalservice.flash.domain.dto;

import lombok.Data;

@Data
public class AppDTO {

    private Long id;

    /**
     * 用户Id
     */
    private Long userId;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用描述
     */
    private String appDesc;

    /**
     * 应用类型：0=html，1=vue3，2=vue3_spring
     */
    private Integer appType;

    /**
     * 应用首页截图
     */
    private String appScreenshot;
}
