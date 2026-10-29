package com.gejia.flashcode.flashcodecommondomain.constants;

/**
 * token相关常量
 */
public class TokenConstants {

    /**
     * 令牌的秘钥
     */
    public final static String SECRET = loadSecret();

    private static String loadSecret() {
        String value = System.getenv("FLASHCODE_JWT_SECRET");
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("FLASHCODE_JWT_SECRET must be provided privately");
        }
        try {
            if (java.util.Base64.getDecoder().decode(value).length < 64) {
                throw new IllegalArgumentException("Insufficient key length");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("FLASHCODE_JWT_SECRET must be Base64 encoding at least 64 random bytes");
        }
        return value;
    }

    /**
     * 令牌前缀
     */
    public final static String PREFIX = "Bearer ";

    /**
     * 登录token   缓存的key
     */
    public final static String LOGIN_TOKEN_KEY = "logintoken:";
}
