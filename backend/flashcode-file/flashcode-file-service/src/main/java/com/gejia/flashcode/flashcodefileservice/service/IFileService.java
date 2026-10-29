package com.gejia.flashcode.flashcodefileservice.service;

import com.gejia.flashcode.flashcodefileservice.domain.dto.FileDTO;
import com.gejia.flashcode.flashcodefileservice.domain.dto.SignDTO;
import org.springframework.web.multipart.MultipartFile;

public interface IFileService {
    FileDTO upload(MultipartFile file);

    SignDTO getSign();
}
