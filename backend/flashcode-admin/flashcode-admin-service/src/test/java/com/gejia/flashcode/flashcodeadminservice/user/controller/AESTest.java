package com.gejia.flashcode.flashcodeadminservice.user.controller;

import cn.hutool.crypto.digest.DigestUtil;
import com.gejia.flashcode.flashcodeadminservice.FlashcodeAdminServiceApplication;
import com.gejia.flashcode.flashcodecommoncore.utils.AESUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = FlashcodeAdminServiceApplication.class)
public class AESTest {

    @Test
    void aesTest() {
        String phoneNumber = "18888888888";
        System.out.println(AESUtil.encryptHex(phoneNumber));
        String password = "123456789";
        System.out.println(AESUtil.encryptHex(password));
        System.out.println(DigestUtil.sha256Hex(password));
    }
}
