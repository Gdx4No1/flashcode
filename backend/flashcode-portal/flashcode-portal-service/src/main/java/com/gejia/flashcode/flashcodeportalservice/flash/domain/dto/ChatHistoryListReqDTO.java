package com.gejia.flashcode.flashcodeportalservice.flash.domain.dto;

import com.gejia.flashcode.flashcodecommondomain.domain.dto.BasePageReqDTO;
import lombok.Data;

import java.io.Serializable;

@Data
public class ChatHistoryListReqDTO extends BasePageReqDTO implements Serializable {

    /**
     * 应用的主键Id
     */
    private Long appId;
}
