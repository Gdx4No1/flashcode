package com.gejia.flashcode.flashcodemstemplateservice;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动类
 */
@Slf4j
@MapperScan("com.gejia.flashcode.**.mapper")
@SpringBootApplication
public class FlashcodeMstemplateServiceApplication {

    /**
     * 主函数
     * @param args 入参
     */
    public static void main(String[] args) {
        SpringApplication.run(FlashcodeMstemplateServiceApplication.class, args);
    }
}
