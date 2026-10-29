package com.gejia.flashcode.flashcodefileservice.controller;

import com.gejia.flashcode.flashcodecommoncore.utils.BeanCopyUtil;
import com.gejia.flashcode.flashcodecommondomain.domain.R;
import com.gejia.flashcode.flashcodefileservice.domain.dto.FileDTO;
import com.gejia.flashcode.flashcodefileservice.domain.dto.SignDTO;
import com.gejia.flashcode.flashcodefileservice.domain.vo.FileVO;
import com.gejia.flashcode.flashcodefileservice.domain.vo.SignVO;
import com.gejia.flashcode.flashcodefileservice.service.IFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Slf4j
public class FileController {

    @Autowired
    private IFileService fileService;

    @PostMapping("/upload")
    public R<FileVO> upload(MultipartFile file) {
        FileDTO fileDTO = fileService.upload(file);
        FileVO fileVO = new FileVO();
        BeanCopyUtil.copyProperties(fileDTO, fileVO);
        return R.ok(fileVO);
    }

    @GetMapping("/sign")
    public R<SignVO> getSign() {
        SignDTO signDTO = fileService.getSign();
        SignVO signVO = new SignVO();
        BeanCopyUtil.copyProperties(signDTO, signVO);
        return R.ok(signVO);
    }
}
