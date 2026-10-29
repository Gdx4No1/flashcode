package com.gejia.flashcode.flashcodeportalservice.flash.domain.dto;

import lombok.Data;

@Data
public class AppEditDTO {

    private Long appId;

    private String elementSelector;

    private String newContent;

}
