package com.gejia.flashcode.flashcodefileapi.feign;

import com.gejia.flashcode.flashcodecommondomain.domain.R;
import com.gejia.flashcode.flashcodefileapi.domin.vo.FileVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(contextId = "FileFeignClient", value = "flashcode-file")
public interface FileFeignClient {

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<FileVO> upload(@RequestPart("file") MultipartFile file);
}
