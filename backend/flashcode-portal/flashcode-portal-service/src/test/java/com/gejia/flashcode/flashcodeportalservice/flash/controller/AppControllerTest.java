package com.gejia.flashcode.flashcodeportalservice.flash.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gejia.flashcode.flashcodecommonsecurity.handler.GlobalExceptionHandler;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.entity.App;
import com.gejia.flashcode.flashcodeportalservice.flash.mapper.AppMapper;
import com.gejia.flashcode.flashcodeportalservice.flash.service.AppQueryService;
import com.gejia.flashcode.flashcodeportalservice.user.entity.dto.UserDTO;
import com.gejia.flashcode.flashcodeportalservice.user.service.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用真实查询服务和 MVC，只隔离数据库与当前用户来源。 */
class AppControllerTest {
    AppMapper mapper; IUserService users; MockMvc mvc;
    @BeforeEach void setUp() {
        mapper = mock(AppMapper.class); users = mock(IUserService.class);
        var user = new UserDTO(); user.setUserId(7L);
        when(users.getLoginUser()).thenReturn(user);
        mvc = MockMvcBuilders.standaloneSetup(new AppController(new AppQueryService(mapper, users)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    App app(long owner) {
        var app = new App(); app.setId(9007199254740993L); app.setUserId(owner);
        app.setAppName("记账本"); app.setAppDesc("记录收支"); app.setAppDoc("# 需求文档");
        return app;
    }
    @Test void ownerCanLoadDetailWithoutLosingIdPrecision() throws Exception {
        when(mapper.selectById(9007199254740993L)).thenReturn(app(7));
        mvc.perform(get("/app/detail").param("appId", "9007199254740993"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200000))
                .andExpect(jsonPath("$.data.id").value("9007199254740993"))
                .andExpect(jsonPath("$.data.appDoc").value("# 需求文档"))
                .andExpect(jsonPath("$.data.appDescription").value("记录收支"))
                .andExpect(jsonPath("$.data.userId").doesNotExist());
    }
    @Test void anotherUsersApplicationIsNotExposed() throws Exception {
        when(mapper.selectById(1L)).thenReturn(app(8));
        mvc.perform(get("/app/detail").param("appId", "1"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.msg").value("应用不存在或无权访问"));
    }
    @Test void nonexistentApplicationUsesSameResponse() throws Exception {
        mvc.perform(get("/app/detail").param("appId", "1"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.msg").value("应用不存在或无权访问"));
    }
    @Test void anonymousRequestCannotQueryDatabase() throws Exception {
        when(users.getLoginUser()).thenReturn(null);
        mvc.perform(get("/app/detail").param("appId", "1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","invalid"})
    void invalidIdIsBadRequest(String id) throws Exception {
        mvc.perform(get("/app/detail").param("appId", id)).andExpect(status().isBadRequest());
        verifyNoInteractions(mapper);
    }
    @Test void missingIdIsBadRequest() throws Exception {
        mvc.perform(get("/app/detail")).andExpect(status().isBadRequest()); verifyNoInteractions(mapper);
    }
    @Test void listUsesCurrentOwnerAndValidatedPagination() throws Exception {
        when(mapper.selectCount(any())).thenReturn(5L);
        when(mapper.selectList(any())).thenAnswer(call -> {
            QueryWrapper<App> query = call.getArgument(0);
            assertTrue(query.getSqlSegment().contains("user_id"));
            assertTrue(query.getSqlSegment().contains("app_type"));
            assertTrue(query.getSqlSegment().endsWith("LIMIT 2, 2"));
            assertTrue(query.getParamNameValuePairs().containsValue(7L));
            assertTrue(query.getParamNameValuePairs().containsValue(0));
            assertFalse(query.getParamNameValuePairs().containsValue(99L));
            return List.of(app(7));
        });
        mvc.perform(post("/app/my/list").contentType("application/json")
                        .content("{\"pageNo\":2,\"pageSize\":2,\"appType\":0,\"userId\":99}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totals").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.list[0].appName").value("记账本"));
    }
    @Test void defaultListCanBeEmpty() throws Exception {
        when(mapper.selectCount(any())).thenReturn(0L); when(mapper.selectList(any())).thenReturn(List.of());
        mvc.perform(post("/app/my/list").contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totals").value(0))
                .andExpect(jsonPath("$.data.list").isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"{\"pageNo\":0}","{\"pageSize\":101}","{\"pageSize\":null}","{\"pageNo\":100001}","{\"appType\":3}"})
    void invalidPaginationDoesNotQueryDatabase(String body) throws Exception {
        mvc.perform(post("/app/my/list").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(mapper);
    }
    @Test void anonymousListDoesNotQueryDatabase() throws Exception {
        when(users.getLoginUser()).thenReturn(null);
        mvc.perform(post("/app/my/list").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized()); verifyNoInteractions(mapper);
    }
}
