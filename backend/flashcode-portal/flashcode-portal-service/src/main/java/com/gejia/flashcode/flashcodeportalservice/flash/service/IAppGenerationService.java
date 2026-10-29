package com.gejia.flashcode.flashcodeportalservice.flash.service;

import com.gejia.flashcode.flashcodeportalservice.flash.domain.dto.AppEditDTO;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.dto.GenerateAppResDTO;

import java.util.List;

public interface IAppGenerationService {
    GenerateAppResDTO appGenerate(Long appId, String appDoc);

    GenerateAppResDTO appEdit(AppEditDTO appEditDTO);

    String vsCodeEdit(Long appId);

    Boolean completeEdit(Long appId);

    String deployApp(Long appId);
}
