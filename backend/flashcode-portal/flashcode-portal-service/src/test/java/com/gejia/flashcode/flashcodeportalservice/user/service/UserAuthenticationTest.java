package com.gejia.flashcode.flashcodeportalservice.user.service;

import com.gejia.flashcode.flashcodeadminapi.appuser.domain.vo.AppUserVo;
import com.gejia.flashcode.flashcodeadminapi.appuser.feign.AppUserFeignClient;
import com.gejia.flashcode.flashcodecommondomain.domain.R;
import com.gejia.flashcode.flashcodecommondomain.domain.ResultCode;
import com.gejia.flashcode.flashcodecommondomain.exception.ServiceException;
import com.gejia.flashcode.flashcodecommonmessage.service.CaptchaService;
import com.gejia.flashcode.flashcodecommonsecurity.domain.dto.TokenDTO;
import com.gejia.flashcode.flashcodecommonsecurity.service.TokenService;
import com.gejia.flashcode.flashcodeportalservice.user.entity.dto.CodeLoginDTO;
import com.gejia.flashcode.flashcodeportalservice.user.entity.dto.LoginDTO;
import com.gejia.flashcode.flashcodeportalservice.user.service.impl.IUserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserAuthenticationTest {
    AppUserFeignClient appUsers;
    TokenService tokens;
    CaptchaService captcha;
    IUserServiceImpl service;

    @BeforeEach void setUp() {
        appUsers = mock(AppUserFeignClient.class);
        tokens = mock(TokenService.class);
        captcha = mock(CaptchaService.class);
        service = new IUserServiceImpl();
        ReflectionTestUtils.setField(service, "appUserFeignClient", appUsers);
        ReflectionTestUtils.setField(service, "tokenService", tokens);
        ReflectionTestUtils.setField(service, "captchaService", captcha);
        when(tokens.createToken(any())).thenReturn(new TokenDTO());
    }

    private CodeLoginDTO login(String code) {
        CodeLoginDTO dto = new CodeLoginDTO();
        dto.setPhone("13800138000");
        dto.setCode(code);
        return dto;
    }

    @Test void invalidCodeDoesNotQueryOrRegisterUser() {
        when(captcha.getCode(anyString())).thenReturn("123456");
        assertThrows(ServiceException.class, () -> service.login(login("000000")));
        verifyNoInteractions(appUsers, tokens);
    }

    @Test void missingCodeDoesNotQueryOrRegisterUser() {
        when(captcha.getCode(anyString())).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.login(login("123456")));
        verifyNoInteractions(appUsers, tokens);
    }

    @Test void providerFailureDoesNotRegisterUser() {
        when(captcha.getCode(anyString())).thenReturn("123456");
        when(appUsers.findByPhone(anyString())).thenReturn(R.fail(ResultCode.ERROR.getCode(), "provider down"));
        assertThrows(ServiceException.class, () -> service.login(login("123456")));
        verify(appUsers, never()).registerByPhone(anyString());
        verifyNoInteractions(tokens);
    }

    @Test void successfulExistingUserCreatesTokenAndDeletesCode() {
        when(captcha.getCode(anyString())).thenReturn("123456");
        AppUserVo user = new AppUserVo();
        user.setUserId(7L); user.setNickName("葛佳");
        when(appUsers.findByPhone(anyString())).thenReturn(R.ok(user));
        assertNotNull(service.login(login("123456")));
        verify(captcha).deleteCode("13800138000");
        verify(tokens).createToken(any());
    }

    @Test void unsupportedLoginTypeIsRejected() {
        assertThrows(ServiceException.class, () -> service.login(new LoginDTO() {}));
        verifyNoInteractions(appUsers, captcha, tokens);
    }

    @Test void nullLoginIsRejected() {
        assertThrows(ServiceException.class, () -> service.login(null));
        verifyNoInteractions(appUsers, captcha, tokens);
    }

    @Test void nullPhoneForSmsIsClientErrorWithoutSending() {
        assertEquals(400000, assertThrows(ServiceException.class, () -> service.sendCode(null)).getCode());
        verifyNoInteractions(captcha);
    }

    @Test void nullPhoneForLoginIsClientError() {
        CodeLoginDTO dto = login("123456"); dto.setPhone(null);
        assertEquals(400000, assertThrows(ServiceException.class, () -> service.login(dto)).getCode());
        verifyNoInteractions(appUsers, captcha, tokens);
    }

    @Test void failedRegistrationNeverCreatesTokenOrConsumesCode() {
        when(captcha.getCode(anyString())).thenReturn("123456");
        when(appUsers.findByPhone(anyString())).thenReturn(R.ok());
        when(appUsers.registerByPhone(anyString())).thenReturn(R.fail(500000, "down"));
        assertThrows(ServiceException.class, () -> service.login(login("123456")));
        verifyNoInteractions(tokens);
        verify(captcha, never()).deleteCode(anyString());
    }

    @Test void wechatProviderFailureDoesNotRegister() {
        var dto = new com.gejia.flashcode.flashcodeportalservice.user.entity.dto.WechatLoginDTO();
        dto.setOpenId("test-open-id");
        when(appUsers.findByOpenId(anyString())).thenReturn(R.fail(500000, "down"));
        assertThrows(ServiceException.class, () -> service.login(dto));
        verify(appUsers, never()).registerByOpenId(anyString());
        verifyNoInteractions(tokens);
    }

    @Test void editWithoutLoginDoesNotReachUserService() {
        var dto = new com.gejia.flashcode.flashcodeadminapi.appuser.domain.dto.UserEditReqDTO();
        dto.setUserId(7L);
        assertEquals(ResultCode.TOKEN_EMPTY.getCode(), assertThrows(ServiceException.class, () -> service.edit(dto)).getCode());
        verifyNoInteractions(appUsers);
    }

    @Test void editAnotherUserIsForbidden() {
        var current = new com.gejia.flashcode.flashcodecommonsecurity.domain.dto.LoginUserDTO();
        current.setUserId(7L); when(tokens.getLoginUser()).thenReturn(current);
        var dto = new com.gejia.flashcode.flashcodeadminapi.appuser.domain.dto.UserEditReqDTO(); dto.setUserId(8L);
        assertEquals(403000, assertThrows(ServiceException.class, () -> service.edit(dto)).getCode());
        verifyNoInteractions(appUsers);
    }

    @Test void editOwnProfileIsAllowed() {
        var current = new com.gejia.flashcode.flashcodecommonsecurity.domain.dto.LoginUserDTO();
        current.setUserId(7L); when(tokens.getLoginUser()).thenReturn(current);
        var dto = new com.gejia.flashcode.flashcodeadminapi.appuser.domain.dto.UserEditReqDTO(); dto.setUserId(7L);
        when(appUsers.edit(dto)).thenReturn(R.ok());
        service.edit(dto);
        verify(appUsers).edit(dto);
    }

    @Test void editProviderFailureIsReported() {
        var current = new com.gejia.flashcode.flashcodecommonsecurity.domain.dto.LoginUserDTO();
        current.setUserId(7L); when(tokens.getLoginUser()).thenReturn(current);
        var dto = new com.gejia.flashcode.flashcodeadminapi.appuser.domain.dto.UserEditReqDTO(); dto.setUserId(7L);
        when(appUsers.edit(dto)).thenReturn(R.fail(500000, "unavailable"));
        assertThrows(ServiceException.class, () -> service.edit(dto));
    }

    @Test void loginInfoUsesLatestDatabaseNicknameNotOldTokenNickname() {
        var current = new com.gejia.flashcode.flashcodecommonsecurity.domain.dto.LoginUserDTO();
        current.setUserId(7L); current.setUserName("old-name");
        when(tokens.getLoginUser()).thenReturn(current);
        AppUserVo user = new AppUserVo(); user.setUserId(7L); user.setNickName("updated-name");
        when(appUsers.findById(7L)).thenReturn(R.ok(user));
        assertEquals("updated-name", service.getLoginUser().convertToVO().getNickName());
    }
}
