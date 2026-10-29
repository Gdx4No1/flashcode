package com.gejia.flashcode.flashcodecommonmessage.service;

import com.gejia.flashcode.flashcodecommondomain.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RefreshScope
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${email.username}")
    private String fromEmail;

    public boolean sendEmailCode(String toEmail, String code) {
        String content = String.format("您的验证码是：%s，请在5分钟内完成验证。如非本人操作，请忽略此邮件。", code);
        return sendSimpleEmail(toEmail, "登录验证码", content);
    }


    public boolean sendSimpleEmail(String toEmail, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(content);

            mailSender.send(message);
            return true;
        } catch (Exception e) {
            throw new ServiceException(e.getMessage());
        }
    }


}
