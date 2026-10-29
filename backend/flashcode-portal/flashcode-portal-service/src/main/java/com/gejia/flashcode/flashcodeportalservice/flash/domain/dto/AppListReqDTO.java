package com.gejia.flashcode.flashcodeportalservice.flash.domain.dto;

import com.gejia.flashcode.flashcodecommondomain.domain.dto.BasePageReqDTO;
import lombok.Data;

import java.io.Serializable;

@Data
public class AppListReqDTO  extends BasePageReqDTO implements Serializable {

    private Integer appType;

    private Integer deployStatus;

    private Long userId;

    private int listType;
}
