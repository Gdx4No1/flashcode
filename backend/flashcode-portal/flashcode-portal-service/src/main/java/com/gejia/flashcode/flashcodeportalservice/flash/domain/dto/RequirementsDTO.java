package com.gejia.flashcode.flashcodeportalservice.flash.domain.dto;

import com.gejia.flashcode.flashcodecommoncore.utils.BeanCopyUtil;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.vo.RequirementsVO;
import lombok.Data;

@Data
public class RequirementsDTO {

    private Long appId;

    private String content;

    public RequirementsVO convertToVO() {
        RequirementsVO requirementsVO = new RequirementsVO();
        BeanCopyUtil.copyProperties(this, requirementsVO);
        return requirementsVO;
    }
}
