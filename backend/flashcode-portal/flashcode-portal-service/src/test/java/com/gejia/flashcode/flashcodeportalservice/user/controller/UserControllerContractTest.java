package com.gejia.flashcode.flashcodeportalservice.user.controller;

import com.gejia.flashcode.flashcodecommonsecurity.domain.dto.TokenDTO;
import com.gejia.flashcode.flashcodecommonsecurity.handler.GlobalExceptionHandler;
import com.gejia.flashcode.flashcodeportalservice.user.entity.dto.UserDTO;
import com.gejia.flashcode.flashcodeportalservice.user.service.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实 MVC 参数绑定和异常处理；服务隔离，不发送短信。 */
class UserControllerContractTest {
    IUserService service; MockMvc mvc;
    @BeforeEach void setUp() {
        service = mock(IUserService.class);
        var controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @ParameterizedTest @ValueSource(strings={"wechat", "code"})
    void loginBindsRequestAndReturnsToken(String kind) throws Exception {
        TokenDTO token = new TokenDTO(); token.setAccessToken("local-test-token");
        when(service.login(any())).thenReturn(token);
        String json = kind.equals("wechat") ? "{\"openId\":\"test-open-id\"}"
                : "{\"phone\":\"13800138000\",\"code\":\"123456\"}";
        mvc.perform(post("/user/login/"+kind).contentType("application/json").content(json))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").value("local-test-token"));
        verify(service).login(argThat(dto -> kind.equals("wechat")
                ? dto instanceof com.gejia.flashcode.flashcodeportalservice.user.entity.dto.WechatLoginDTO d && d.getOpenId().equals("test-open-id")
                : dto instanceof com.gejia.flashcode.flashcodeportalservice.user.entity.dto.CodeLoginDTO codeDto && codeDto.getCode().equals("123456")));
    }
    @Test void sendCodeDelegatesOnlyToMock() throws Exception {
        when(service.sendCode("13800138000")).thenReturn("test-sent");
        mvc.perform(get("/user/send_code").param("phone", "13800138000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("test-sent"));
        verify(service).sendCode("13800138000");
    }
    @Test void editBindsUserFields() throws Exception {
        mvc.perform(post("/user/edit").contentType("application/json")
                .content("{\"userId\":7,\"nickName\":\"local-user\",\"avtar\":\"avatar.png\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200000));
        verify(service).edit(argThat(d -> d.getUserId()==7L && d.getNickName().equals("local-user")));
    }
    @Test void loginInfoConvertsNickname() throws Exception {
        UserDTO dto=new UserDTO(); dto.setUserId(7L); dto.setUserName("local-user");
        when(service.getLoginUser()).thenReturn(dto);
        mvc.perform(get("/user/login_info/get")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickName").value("local-user"));
        verify(service).getLoginUser();
    }
    @Test void logoutDelegates() throws Exception {
        mvc.perform(delete("/user/logout")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200000));
        verify(service).logout();
    }
    @ParameterizedTest @ValueSource(strings={"", "{", "{}", "{\"phone\":\"13800138000\"}"})
    void invalidLoginBodyIsRejected(String body) throws Exception {
        mvc.perform(post("/user/login/code").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400000));
        verifyNoInteractions(service);
    }
    @Test void missingPhoneIsRejected() throws Exception {
        mvc.perform(get("/user/send_code")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void invalidEditIsRejected() throws Exception {
        mvc.perform(post("/user/edit").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
