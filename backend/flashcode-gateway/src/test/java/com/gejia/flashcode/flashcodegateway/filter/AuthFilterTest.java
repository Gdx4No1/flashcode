package com.gejia.flashcode.flashcodegateway.filter;

import com.gejia.flashcode.flashcodecommonredis.service.RedisService;
import com.gejia.flashcode.flashcodecommonsecurity.utils.JwtUtil;
import com.gejia.flashcode.flashcodegateway.config.IgnoreWhiteProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthFilterTest {
    AuthFilter filter;
    RedisService redis;
    GatewayFilterChain chain;
    @BeforeEach void setup() {
        filter = new AuthFilter(); redis = mock(RedisService.class); chain = mock(GatewayFilterChain.class);
        var whites = new IgnoreWhiteProperties(); whites.setWhites(List.of("/portal/user/send_code"));
        ReflectionTestUtils.setField(filter, "redisService", redis);
        ReflectionTestUtils.setField(filter, "ignoreWhiteProperties", whites);
    }
    private MockServerWebExchange request(String token) {
        var builder = MockServerHttpRequest.get("/portal/user/login_info/get");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return MockServerWebExchange.from(builder.build());
    }
    @Test void missingTokenIs401() {
        var request=request(null); filter.filter(request,chain).block();
        assertEquals(HttpStatus.UNAUTHORIZED,request.getResponse().getStatusCode());
        verifyNoInteractions(redis,chain);
    }
    @Test void malformedTokenIs401Not500() {
        var request=request("not-a-valid-jwt"); filter.filter(request,chain).block();
        assertEquals(HttpStatus.UNAUTHORIZED,request.getResponse().getStatusCode());
        verifyNoInteractions(redis,chain);
    }
    @Test void tamperedSignatureIs401() {
        String token=JwtUtil.createToken(Map.of("user_key","test"));
        String[] parts=token.split("\\."); parts[2]=(parts[2].charAt(0)=='A'?'B':'A')+parts[2].substring(1);
        var request=request(String.join(".",parts)); filter.filter(request,chain).block();
        assertEquals(HttpStatus.UNAUTHORIZED,request.getResponse().getStatusCode());
        verifyNoInteractions(redis,chain);
    }
    @Test void expiredJwtIs401() {
        var request=request(JwtUtil.createToken(Map.of("exp",1,"user_key","test")));
        filter.filter(request,chain).block();
        assertEquals(HttpStatus.UNAUTHORIZED,request.getResponse().getStatusCode());
        verifyNoInteractions(redis,chain);
    }
    @Test void missingUserKeyCannotUseEmptyRedisKey() {
        var request=request(JwtUtil.createToken(Map.of("user_id",7)));
        filter.filter(request,chain).block();
        assertEquals(HttpStatus.UNAUTHORIZED,request.getResponse().getStatusCode());
        verifyNoInteractions(redis,chain);
    }
    @Test void tokenWithoutRedisSessionIs401() {
        var request=request(JwtUtil.createToken(Map.of("user_key","test")));
        filter.filter(request,chain).block();
        assertEquals(HttpStatus.UNAUTHORIZED,request.getResponse().getStatusCode());
        verify(redis).hasKey("logintoken:test"); verifyNoInteractions(chain);
    }
    @Test void anonymousCaseReadsAreAllowedWithExactMethodAndPath() {
        when(chain.filter(any())).thenReturn(reactor.core.publisher.Mono.empty());
        for (var request : List.of(MockServerHttpRequest.post("/portal/app/list"),
                MockServerHttpRequest.get("/portal/app/public/detail?appId=42"))) {
            filter.filter(MockServerWebExchange.from(request.build()),chain).block();
        }
        verify(chain,times(2)).filter(any()); verifyNoInteractions(redis);
    }
    @Test void publicCasePolicyDoesNotExposeOtherMethodsOrPrivateEndpoints() {
        for (var request : List.of(MockServerHttpRequest.get("/portal/app/list"),
                MockServerHttpRequest.post("/portal/app/public/detail"),
                MockServerHttpRequest.get("/portal/app/detail?appId=42"),
                MockServerHttpRequest.post("/portal/app/chat/history/list"),
                MockServerHttpRequest.post("/portal/flash/apps/deploy"),
                MockServerHttpRequest.post("/portal/app/list/private"))) {
            var exchange=MockServerWebExchange.from(request.build()); filter.filter(exchange,chain).block();
            assertEquals(HttpStatus.UNAUTHORIZED,exchange.getResponse().getStatusCode());
        }
        verifyNoInteractions(redis,chain);
    }
}
