package com.gejia.flashcode.flashcodeportalservice.flash.domain.dto;

import com.gejia.flashcode.flashcodecommoncore.utils.BeanCopyUtil;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.vo.AppDetailVO;
import lombok.Data;

@Data
public class AppDetailDTO {

    /**
     * 主键id
     */
    private Long id;

    /**
     * 所属用户主键ID
     */
    private Long userId;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用类型：0=html，1=vue3，2=vue3_spring
     */
    private Integer appType;

    /**
     * 预览的url
     */
    private String previewUrl;

    public AppDetailVO convert2VO() {
        AppDetailVO appDetailVO = new AppDetailVO();
        BeanCopyUtil.copyProperties(this, appDetailVO);
        return appDetailVO;
    }
}
