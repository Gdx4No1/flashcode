package com.gejia.flashcode.flashcodeportalservice;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 
 */
@Slf4j
@MapperScan("com.gejia.flashcode.**.mapper")
@EnableFeignClients(basePackages = {"com.gejia.flashcode.**.feign"})
@SpringBootApplication
public class FlashcodePortalServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlashcodePortalServiceApplication.class, args);
        log.info("门户服务启动成功");
    }
}