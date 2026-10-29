package com.gejia.flashcode.flashcodeportalservice.flash.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gejia.flashcode.flashcodecommoncore.domain.entity.BaseDO;
import lombok.Data;

@Data
@TableName("app")
public class App extends BaseDO {

    /**
     * 所属用户主键ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 应用名称
     */
    @TableField("app_name")
    private String appName;

    /**
     * 应用描述
     */
    @TableField("app_desc")
    private String appDesc;

    /**
     * 应用类型：0=html，1=vue3，2=vue3_spring
     */
    @TableField("app_type")
    private Integer appType;

    /**
     * 应用需求文档
     */
    @TableField("app_doc")
    private String appDoc;

    /**
     * 应用首页截图
     */
    @TableField("app_screenshot")
    private String appScreenshot;

    /**
     * 预览的url
     */
    @TableField("preview_url")
    private String previewUrl;

    /**
     * 部署状态： 0=未部署  1=已部署
     */
    @TableField("deploy_status")
    private Integer deployStatus;

    /**
     * 应用部署的url
     */
    @TableField("app_url")
    private String appUrl;
}
