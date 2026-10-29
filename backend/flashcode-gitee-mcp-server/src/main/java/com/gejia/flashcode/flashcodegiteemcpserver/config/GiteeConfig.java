package com.gejia.flashcode.flashcodegiteemcpserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "gitee")
public class GiteeConfig {

    /**
     * Gitee API 的基础 URL
     */
    private String apiBaseUrl;

    /**
     * Gitee API 的访问令牌
     */
    private String accessToken;

}
