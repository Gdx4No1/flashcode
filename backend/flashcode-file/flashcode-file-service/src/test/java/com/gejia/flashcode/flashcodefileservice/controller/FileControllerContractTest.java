package com.gejia.flashcode.flashcodefileservice.controller;

import com.gejia.flashcode.flashcodecommonsecurity.handler.GlobalExceptionHandler;
import com.gejia.flashcode.flashcodecommondomain.exception.ServiceException;
import com.gejia.flashcode.flashcodefileservice.domain.dto.FileDTO;
import com.gejia.flashcode.flashcodefileservice.domain.dto.SignDTO;
import com.gejia.flashcode.flashcodefileservice.service.IFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用内存文件和 Mock OSS 服务，不上传任何真实文件。 */
class FileControllerContractTest {
    IFileService service; MockMvc mvc;
    @BeforeEach void setUp() {
        service=mock(IFileService.class);
        var controller=new FileController();
        ReflectionTestUtils.setField(controller, "fileService", service);
        mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void uploadReturnsMetadataAndBindsFile() throws Exception {
        FileDTO dto=new FileDTO(); dto.setKey("test/demo.txt"); dto.setName("demo.txt"); dto.setUrl("https://files.example/demo.txt");
        when(service.upload(any())).thenReturn(dto);
        mvc.perform(multipart("/upload").file(new MockMultipartFile("file","demo.txt","text/plain",new byte[]{1,2,3})))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200000))
                .andExpect(jsonPath("$.data.key").value("test/demo.txt"))
                .andExpect(jsonPath("$.data.url").value("https://files.example/demo.txt"));
        verify(service).upload(argThat(f -> f.getSize()==3 && "demo.txt".equals(f.getOriginalFilename())));
    }
    @Test void signReturnsMockSignature() throws Exception {
        SignDTO dto=new SignDTO(); dto.setSignature("test-signature"); dto.setHost("https://files.example");
        when(service.getSign()).thenReturn(dto);
        mvc.perform(get("/sign")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signature").value("test-signature"));
        verify(service).getSign();
    }
    @Test void missingFileIsBadRequest() throws Exception {
        mvc.perform(multipart("/upload")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400000));
        verifyNoInteractions(service);
    }
    @Test void emptyFileIsBadRequest() throws Exception {
        mvc.perform(multipart("/upload").file(new MockMultipartFile("file",new byte[0])))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void storageFailureReturnsUnifiedError() throws Exception {
        when(service.getSign()).thenThrow(new ServiceException("storage unavailable"));
        mvc.perform(get("/sign")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500000));
    }
}
