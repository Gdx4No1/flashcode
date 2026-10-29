package com.gejia.flashcode.flashcodeadminservice.user.controller;

import com.gejia.flashcode.flashcodeadminservice.FlashcodeAdminServiceApplication;
import com.gejia.flashcode.flashcodecommonsecurity.domain.dto.LoginUserDTO;
import com.gejia.flashcode.flashcodecommonsecurity.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = FlashcodeAdminServiceApplication.class)
public class TokenTest {
    @Autowired
    private TokenService tokenService;

    @Test
    void tokenTest() {
        LoginUserDTO loginUserDTO = new LoginUserDTO();
        loginUserDTO.setUserId(100L);
        loginUserDTO.setUserName("zhangSan");
        loginUserDTO.setUserFrom("sys");
        tokenService.createToken(loginUserDTO);
    }
}
