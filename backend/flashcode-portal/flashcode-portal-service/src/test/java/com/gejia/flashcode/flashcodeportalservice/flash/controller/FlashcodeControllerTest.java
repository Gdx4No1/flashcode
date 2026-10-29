package com.gejia.flashcode.flashcodeportalservice.flash.controller;

import com.gejia.flashcode.flashcodecommondomain.domain.ResultCode;
import com.gejia.flashcode.flashcodecommondomain.exception.ServiceException;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.dto.GenerateAppResDTO;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.dto.RequirementsDTO;
import com.gejia.flashcode.flashcodeportalservice.flash.service.AppGenerationService;
import com.gejia.flashcode.flashcodeportalservice.flash.service.RequirementsService;
import com.gejia.flashcode.flashcodecommonsecurity.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FlashcodeControllerTest {
    private RequirementsService requirements;
    private AppGenerationService generation;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        requirements = mock(RequirementsService.class);
        generation = mock(AppGenerationService.class);
        FlashcodeController controller = new FlashcodeController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "requirementsService", requirements);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "appGenerationService", generation);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void requirementsGenerateReturnsApplicationIdAndDocument() throws Exception {
        RequirementsDTO result = new RequirementsDTO();
        result.setAppId(1001L);
        result.setContent("# 应用需求文档");
        when(requirements.requirementsGenerate("做一个记账本")).thenReturn(result);

        mvc.perform(post("/flash/requirements/generate").param("input", "做一个记账本"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.appId").value("1001"))
                .andExpect(jsonPath("$.data.content").value("# 应用需求文档"));
        verify(requirements).requirementsGenerate("做一个记账本");
    }

    @Test
    void appGenerateReturnsPreviewMetadata() throws Exception {
        when(generation.appGenerate(1001L, "生成页面"))
                .thenReturn(new GenerateAppResDTO(1001L, 0, "http://preview.test/1001/"));

        mvc.perform(post("/flash/apps/generate")
                        .param("appId", "1001")
                        .param("requirement", "生成页面"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.appId").value("1001"))
                .andExpect(jsonPath("$.data.appType").value(0))
                .andExpect(jsonPath("$.data.previewUrl").value("http://preview.test/1001/"));
        verify(generation).appGenerate(1001L, "生成页面");
    }

    @Test
    void missingRequirementsInputIsRejectedBeforeServiceCall() throws Exception {
        mvc.perform(post("/flash/requirements/generate"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(requirements);
    }

    @Test
    void invalidApplicationIdTypeIsRejectedBeforeServiceCall() throws Exception {
        mvc.perform(post("/flash/apps/generate")
                        .param("appId", "not-a-number")
                        .param("requirement", "生成页面"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(generation);
    }

    @Test
    void businessFailureUsesUnifiedErrorResponse() throws Exception {
        when(requirements.requirementsGenerate("失败需求"))
                .thenThrow(new ServiceException("模型暂时不可用", ResultCode.ERROR.getCode()));

        mvc.perform(post("/flash/requirements/generate").param("input", "失败需求"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.code").value(ResultCode.ERROR.getCode()))
                .andExpect(jsonPath("$.msg").value("模型暂时不可用"));
    }

    @Test void blankRequirementsAreRejected() throws Exception {
        mvc.perform(post("/flash/requirements/generate").param("input", "  "))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400000));
        verifyNoInteractions(requirements);
    }

    @Test void attachmentOnlyMultipartReachesModelServiceWithExtractedContent() throws Exception {
        var dto=new RequirementsDTO();dto.setAppId(1002L);dto.setContent("附件需求");
        when(requirements.requirementsGenerate(contains("附件专属蓝鲸番茄钟"))).thenReturn(dto);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/flash/requirements/generate")
                .file(new org.springframework.mock.web.MockMultipartFile("sketch","requirements.txt","text/plain","附件专属蓝鲸番茄钟".getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.appId").value("1002"));
        verify(requirements).requirementsGenerate(contains("附件专属蓝鲸番茄钟"));
    }
    @Test void unsupportedAttachmentIsRejectedEvenWhenTextExists() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/flash/requirements/generate")
                .file(new org.springframework.mock.web.MockMultipartFile("sketch","sketch.png","image/png",new byte[]{1,2,3})).param("input","创建应用"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("支持TXT")));
        verifyNoInteractions(requirements);
    }

    @Test void missingAppIdIsRejected() throws Exception {
        mvc.perform(post("/flash/apps/generate").param("requirement", "页面"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(generation);
    }

    @Test void missingRequirementIsRejected() throws Exception {
        mvc.perform(post("/flash/apps/generate").param("appId", "1"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(generation);
    }

    @ParameterizedTest @ValueSource(longs = {0, -1})
    void invalidAppIdIsRejected(long id) throws Exception {
        mvc.perform(post("/flash/apps/generate").param("appId", String.valueOf(id)).param("requirement", "页面"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(generation);
    }

    @Test void blankAppRequirementIsRejected() throws Exception {
        mvc.perform(post("/flash/apps/generate").param("appId", "1").param("requirement", "  "))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(generation);
    }

    @Test void wrongHttpMethodIsRejected() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/flash/apps/generate"))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(generation);
    }

    @Test void teacherAppDocParameterIsAlsoAccepted() throws Exception {
        when(generation.appGenerate(1001L, "需求")).thenReturn(new GenerateAppResDTO(1001L,0,"https://preview.test/1001/"));
        mvc.perform(post("/flash/apps/generate").param("appId","1001").param("appDoc","需求"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.appId").value("1001"));
    }
    @Test void editMatchesFrontendJsonContract() throws Exception {
        when(generation.appEdit(any())).thenReturn(new GenerateAppResDTO(1001L,0,"https://preview.test/1001/"));
        mvc.perform(post("/flash/apps/edit").contentType("application/json").content("{\"appId\":\"1001\",\"newContent\":\"改标题\",\"elementSelector\":\"h1\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.previewUrl").value("https://preview.test/1001/"));
    }
    @Test void blankEditIsRejectedBeforeService() throws Exception {
        mvc.perform(post("/flash/apps/edit").contentType("application/json").content("{\"appId\":1,\"newContent\":\" \"}"))
            .andExpect(status().isBadRequest()); verifyNoInteractions(generation);
    }
    @Test void vscodeReturnsStringUrl() throws Exception {
        when(generation.vsCodeEdit(1L)).thenReturn("https://editor.test/?folder=1");
        mvc.perform(post("/flash/apps/vscode/edit").contentType("application/json").content("{\"appId\":\"1\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("https://editor.test/?folder=1"));
    }
    @Test void completeEditReturnsBoolean() throws Exception {
        when(generation.completeEdit(1L)).thenReturn(true);
        mvc.perform(post("/flash/app/complete-edit").contentType("application/json").content("{\"appId\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
    }
    @Test void deployReturnsStringUrl() throws Exception {
        when(generation.deployApp(1L)).thenReturn("https://release.test/1/");
        mvc.perform(post("/flash/apps/deploy").contentType("application/json").content("{\"appId\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("https://release.test/1/"));
    }
    @ParameterizedTest @ValueSource(strings={"/flash/apps/edit","/flash/apps/vscode/edit","/flash/app/complete-edit","/flash/apps/deploy"})
    void invalidIdNeverReachesWriteService(String path) throws Exception {
        mvc.perform(post(path).contentType("application/json").content("{\"appId\":-1,\"newContent\":\"修改\"}"))
            .andExpect(status().isBadRequest()); verifyNoInteractions(generation);
    }
}
