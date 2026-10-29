package com.gejia.flashcode.flashcodeportalservice.flash.service;

import com.gejia.flashcode.flashcodeportalservice.flash.domain.dto.RequirementsDTO;

public interface IRequirementsService {

    RequirementsDTO requirementsGenerate(String input);
}
