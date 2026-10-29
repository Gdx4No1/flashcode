package com.gejia.flashcode.flashcodeportalservice.flash.domain.dto;

import com.gejia.flashcode.flashcodecommoncore.utils.BeanCopyUtil;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.vo.GenerateAppResVO;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GenerateAppResDTO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long appId;

    private String previewUrl;

    private String appType;

    public GenerateAppResVO convertToVO() {
        GenerateAppResVO generateAppResVO = new GenerateAppResVO();
        BeanCopyUtil.copyProperties(this, generateAppResVO);
        return generateAppResVO;
    }
}
