package com.gejia.flashcode.flashcodeportalservice.user.controller;
import com.gejia.flashcode.flashcodecommonmessage.enums.SendType;
import com.gejia.flashcode.flashcodecommonmessage.service.AliSmsService;
import com.gejia.flashcode.flashcodecommonmessage.service.CaptchaService;
import com.gejia.flashcode.flashcodeportalservice.FlashcodePortalServiceApplication;
import com.gejia.flashcode.flashcodeportalservice.user.entity.dto.CodeLoginDTO;
import com.gejia.flashcode.flashcodeportalservice.user.entity.dto.WechatLoginDTO;
import com.gejia.flashcode.flashcodeportalservice.user.service.IUserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * C端用户服务单元测试
 */
@SpringBootTest(classes = FlashcodePortalServiceApplication.class)
public class UserControllerTest {

    @Autowired
    private IUserService userService;

    @Autowired
    private AliSmsService aliSmsService;

    @Autowired
    private CaptchaService captchaService;

    @Test
    void login() {
        WechatLoginDTO wechatLoginDTO = new WechatLoginDTO();
        wechatLoginDTO.setOpenId("123456789");
        Assertions.assertTrue(userService.login(wechatLoginDTO) != null);
    }

    @Test
    void sendMessage() {
        aliSmsService.sendMobileCode("15399385964", "123456");
    }

    @Test
    void captcha() {
        Assertions.assertTrue(captchaService.sendCode("15399385964", SendType.SMS.getValue()) != null);
    }

    @Test
    void sendCode() {
        Assertions.assertTrue(userService.sendCode("18888888888") != null);
    }

    @Test
    void loginByCode() {
        String phone = "18888888888";
        String code = captchaService.sendCode(phone, SendType.SMS.getValue());
        CodeLoginDTO codeLoginDTO = new CodeLoginDTO();
        codeLoginDTO.setPhone(phone);
        codeLoginDTO.setCode(code);
        Assertions.assertTrue(userService.login(codeLoginDTO) != null);
    }
}
