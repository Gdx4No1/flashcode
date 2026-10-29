package com.gejia.flashcode.flashcodeadminservice.contract;

import com.gejia.flashcode.flashcodeadminapi.appuser.domain.dto.*;
import com.gejia.flashcode.flashcodeadminapi.config.domain.dto.*;
import com.gejia.flashcode.flashcodeadminservice.config.controller.*;
import com.gejia.flashcode.flashcodeadminservice.config.service.*;
import com.gejia.flashcode.flashcodeadminservice.map.controller.FlashcodeMapController;
import com.gejia.flashcode.flashcodeadminservice.map.domain.dto.*;
import com.gejia.flashcode.flashcodeadminservice.map.service.IFlashcodeMapService;
import com.gejia.flashcode.flashcodeadminservice.user.contorller.*;
import com.gejia.flashcode.flashcodeadminservice.user.domain.dto.*;
import com.gejia.flashcode.flashcodeadminservice.user.service.*;
import com.gejia.flashcode.flashcodecommoncore.domain.dto.BasePageDTO;
import com.gejia.flashcode.flashcodecommondomain.domain.vo.BasePageVO;
import com.gejia.flashcode.flashcodecommondomain.exception.ServiceException;
import com.gejia.flashcode.flashcodecommonsecurity.domain.dto.TokenDTO;
import com.gejia.flashcode.flashcodecommonsecurity.handler.GlobalExceptionHandler;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 后台全部业务路由的隔离契约测试，不启动 Nacos、不访问数据库。 */
class AdminControllerContractTest {
    ISysArgumentService arguments; ISysDictionaryService dictionaries;
    IAppUserService appUsers; ISysUserService sysUsers; IFlashcodeMapService maps; MockMvc mvc;
    @BeforeEach void setUp() {
        arguments=mock(ISysArgumentService.class); dictionaries=mock(ISysDictionaryService.class);
        appUsers=mock(IAppUserService.class); sysUsers=mock(ISysUserService.class); maps=mock(IFlashcodeMapService.class);
        var ac=new ArgumentController(); var dc=new DictionaryController();
        var uc=new AppUserController(); var sc=new SysUserController(); var mc=new FlashcodeMapController();
        ReflectionTestUtils.setField(ac,"iSysArgumentService",arguments);
        ReflectionTestUtils.setField(dc,"iSysDictionaryService",dictionaries);
        ReflectionTestUtils.setField(uc,"appUserService",appUsers);
        ReflectionTestUtils.setField(sc,"sysUserService",sysUsers);
        ReflectionTestUtils.setField(mc,"mapService",maps);
        mvc=MockMvcBuilders.standaloneSetup(ac,dc,uc,sc,mc).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    MockHttpServletRequestBuilder json(String path,String body) { return post(path).contentType("application/json").content(body); }
    ResultActions ok(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200000));
    }
    AppUserDTO user() { var d=new AppUserDTO(); d.setUserId(7L); d.setNickName("local-user"); return d; }
    <T> BasePageDTO<T> page(List<T> list) { var d=new BasePageDTO<T>(); d.setList(list); d.setTotals(list.size()); d.setTotalPages(1); return d; }

    @ParameterizedTest @ValueSource(strings={"add","edit"})
    void argumentWrite(String action) throws Exception {
        when(arguments.add(any())).thenReturn(7L); when(arguments.edit(any())).thenReturn(7L);
        ok(json("/argument/"+action,"{\"configKey\":\"site.name\",\"name\":\"站点\",\"value\":\"flashcode\"}")).andExpect(jsonPath("$.data").value(7));
        if(action.equals("add")) verify(arguments).add(argThat(d -> d.getConfigKey().equals("site.name")));
        else verify(arguments).edit(argThat(d -> d.getValue().equals("flashcode")));
    }
    @Test void argumentList() throws Exception {
        var p=new BasePageVO<com.gejia.flashcode.flashcodeadminapi.config.domain.vo.ArgumentVO>(); p.setList(List.of()); p.setTotals(0);
        when(arguments.list(any())).thenReturn(p);
        ok(get("/argument/list")).andExpect(jsonPath("$.data.totals").value(0)); verify(arguments).list(any());
    }
    @Test void argumentKey() throws Exception {
        var d=new ArgumentDTO(); d.setConfigKey("site.name"); d.setValue("flashcode");
        when(arguments.getByConfigKey("site.name")).thenReturn(d);
        mvc.perform(get("/argument/key").param("configKey","site.name")).andExpect(status().isOk()).andExpect(jsonPath("$.value").value("flashcode"));
        verify(arguments).getByConfigKey("site.name");
    }
    @Test void argumentKeys() throws Exception {
        var d=new ArgumentDTO(); d.setConfigKey("a"); when(arguments.getByConfigKeys(List.of("a","b"))).thenReturn(List.of(d));
        mvc.perform(get("/argument/keys").param("configKeys","a","b")).andExpect(status().isOk()).andExpect(jsonPath("$[0].configKey").value("a"));
        verify(arguments).getByConfigKeys(List.of("a","b"));
    }
    @ParameterizedTest @ValueSource(strings={"add","edit"})
    void dictionaryTypeWrite(String action) throws Exception {
        when(dictionaries.addType(any())).thenReturn(8L); when(dictionaries.editType(any())).thenReturn(8L);
        ok(json("/dictionary_type/"+action,"{\"typeKey\":\"status\",\"value\":\"状态\"}")).andExpect(jsonPath("$.data").value(8));
        if(action.equals("add")) verify(dictionaries).addType(argThat(d -> d.getTypeKey().equals("status")));
        else verify(dictionaries).editType(argThat(d -> d.getValue().equals("状态")));
    }
    @Test void dictionaryTypeList() throws Exception {
        var p=new BasePageVO<com.gejia.flashcode.flashcodeadminapi.config.domain.vo.DictionaryTypeVO>(); p.setTotals(0); p.setList(List.of());
        when(dictionaries.listType(any())).thenReturn(p); ok(get("/dictionary_type/list")).andExpect(jsonPath("$.data.totals").value(0));
        verify(dictionaries).listType(any());
    }
    @ParameterizedTest @ValueSource(strings={"add","edit"})
    void dictionaryDataWrite(String action) throws Exception {
        when(dictionaries.addData(any())).thenReturn(9L); when(dictionaries.editData(any())).thenReturn(9L);
        ok(json("/dictionary_data/"+action,"{\"typeKey\":\"status\",\"dataKey\":\"active\",\"value\":\"启用\"}")).andExpect(jsonPath("$.data").value(9));
        if(action.equals("add")) verify(dictionaries).addData(argThat(d -> d.getDataKey().equals("active")));
        else verify(dictionaries).editData(argThat(d -> d.getDataKey().equals("active")));
    }
    @Test void dictionaryDataList() throws Exception {
        var p=new BasePageVO<com.gejia.flashcode.flashcodeadminapi.config.domain.vo.DictionaryDataVo>(); p.setTotals(0); p.setList(List.of());
        when(dictionaries.listData(any())).thenReturn(p); ok(get("/dictionary_data/list").param("typeKey","status")).andExpect(jsonPath("$.data.totals").value(0));
        verify(dictionaries).listData(any());
    }
    DictionaryDataDTO dictionary() { var d=new DictionaryDataDTO(); d.setDataKey("active"); d.setValue("启用"); return d; }
    @Test void dictionaryByType() throws Exception {
        when(dictionaries.selectDictDataByType("status")).thenReturn(List.of(dictionary()));
        mvc.perform(get("/dictionary_data/type").param("typeKey","status")).andExpect(status().isOk()).andExpect(jsonPath("$[0].dataKey").value("active"));
        verify(dictionaries).selectDictDataByType("status");
    }
    @Test void dictionaryByTypes() throws Exception {
        when(dictionaries.selectDictDataByTypes(List.of("status"))).thenReturn(Map.of("status",List.of(dictionary())));
        mvc.perform(json("/dictionary_data/types","[\"status\"]")).andExpect(status().isOk()).andExpect(jsonPath("$.status[0].dataKey").value("active"));
        verify(dictionaries).selectDictDataByTypes(List.of("status"));
    }
    @Test void dictionaryByKey() throws Exception {
        when(dictionaries.getDicDataByKey("active")).thenReturn(dictionary());
        mvc.perform(get("/dictionary_data/key").param("dataKey","active")).andExpect(status().isOk()).andExpect(jsonPath("$.dataKey").value("active"));
        verify(dictionaries).getDicDataByKey("active");
    }
    @Test void dictionaryByKeys() throws Exception {
        when(dictionaries.getDicDataByKeys(List.of("active"))).thenReturn(List.of(dictionary()));
        mvc.perform(json("/dictionary_data/keys","[\"active\"]")).andExpect(status().isOk()).andExpect(jsonPath("$[0].dataKey").value("active"));
        verify(dictionaries).getDicDataByKeys(List.of("active"));
    }
    @ParameterizedTest @ValueSource(strings={"register/openid","open_id_find","phone_find","register/phone","id_find"})
    void appUserQueries(String path) throws Exception {
        when(appUsers.registerByOpenId("test-id")).thenReturn(user()); when(appUsers.findByOpenId("test-id")).thenReturn(user());
        when(appUsers.findByPhone("13800138000")).thenReturn(user()); when(appUsers.registerByPhone("13800138000")).thenReturn(user());
        when(appUsers.findById(7L)).thenReturn(user());
        ok(get("/app_user/"+path).param("openId","test-id").param("phoneNumber","13800138000").param("userId","7"))
                .andExpect(jsonPath("$.data.nickName").value("local-user"));
        switch(path) {
            case "register/openid" -> verify(appUsers).registerByOpenId("test-id");
            case "open_id_find" -> verify(appUsers).findByOpenId("test-id");
            case "phone_find" -> verify(appUsers).findByPhone("13800138000");
            case "register/phone" -> verify(appUsers).registerByPhone("13800138000");
            default -> verify(appUsers).findById(7L);
        }
    }
    @Test void appUserEdit() throws Exception {
        ok(json("/app_user/edit","{\"userId\":7,\"nickName\":\"local-user\",\"avtar\":\"avatar.png\"}"));
        verify(appUsers).edit(argThat(d -> d.getUserId()==7L));
    }
    @Test void appUserListByIds() throws Exception {
        when(appUsers.getUserList(List.of(7L))).thenReturn(Arrays.asList(user(),null));
        ok(json("/app_user/list","[7]")).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].nickName").value("local-user"));
        verify(appUsers).getUserList(List.of(7L));
    }
    @Test void appUserSearch() throws Exception {
        when(appUsers.getUserList(any(AppUserListReqDTO.class))).thenReturn(page(List.of(user())));
        ok(json("/app_user/list/search","{}")).andExpect(jsonPath("$.data.totals").value(1))
                .andExpect(jsonPath("$.data.list[0].nickName").value("local-user"));
        verify(appUsers).getUserList(any(AppUserListReqDTO.class));
    }
    @Test void sysLogin() throws Exception {
        var token=new TokenDTO(); token.setAccessToken("test-token"); when(sysUsers.login(any())).thenReturn(token);
        ok(json("/sys_user/login/password","{\"phone\":\"13800138000\",\"password\":\"test123\"}"))
                .andExpect(jsonPath("$.data.accessToken").value("test-token"));
        verify(sysUsers).login(argThat(d -> d.getPhone().equals("13800138000")));
    }
    @Test void sysWrite() throws Exception {
        when(sysUsers.addOrEdit(any())).thenReturn(7L);
        ok(json("/sys_user/add_edit","{\"nickName\":\"local-user\",\"status\":\"1\"}")).andExpect(jsonPath("$.data").value(7));
        verify(sysUsers).addOrEdit(argThat(d -> d.getNickName().equals("local-user")));
    }
    @Test void sysListDoesNotLeakPassword() throws Exception {
        var d=new SysUserDTO(); d.setUserId(7L); d.setNickName("local-user"); d.setPassword("do-not-expose");
        when(sysUsers.getUserList(any())).thenReturn(List.of(d));
        ok(json("/sys_user/list","{}")).andExpect(jsonPath("$.data[0].nickName").value("local-user"))
                .andExpect(jsonPath("$.data[0].password").doesNotExist());
        verify(sysUsers).getUserList(any());
    }
    @Test void sysLoginInfo() throws Exception {
        var d=new SysUserLoginDTO(); d.setUserId(7L); d.setNickName("local-user"); when(sysUsers.getLoginUser()).thenReturn(d);
        ok(get("/sys_user/login_info/get")).andExpect(jsonPath("$.data.nickName").value("local-user")); verify(sysUsers).getLoginUser();
    }
    SysRegionDTO city() { var d=new SysRegionDTO(); d.setId(1L); d.setName("测试城市"); return d; }
    @ParameterizedTest @ValueSource(strings={"city_list","city_hot_list","region_children_list"})
    void mapCityLists(String path) throws Exception {
        when(maps.getCityList()).thenReturn(List.of(city())); when(maps.getHotCityList()).thenReturn(List.of(city()));
        when(maps.getRegionChildren(1L)).thenReturn(List.of(city()));
        ok(get("/map/"+path).param("parentId","1")).andExpect(jsonPath("$.data[0].name").value("测试城市"));
        switch(path) { case "city_list" -> verify(maps).getCityList(); case "city_hot_list" -> verify(maps).getHotCityList(); default -> verify(maps).getRegionChildren(1L); }
    }
    @Test void mapPinyin() throws Exception {
        when(maps.getCityPylist()).thenReturn(Map.of("C",List.of(city())));
        ok(get("/map/city_pinyin_list")).andExpect(jsonPath("$.data.C[0].name").value("测试城市")); verify(maps).getCityPylist();
    }
    @Test void mapSearch() throws Exception {
        when(maps.searchSuggestOnMap(any())).thenReturn(page(List.of()));
        ok(json("/map/search","{\"keyword\":\"公园\",\"id\":1,\"pageNo\":1,\"pageSize\":10}"))
                .andExpect(jsonPath("$.data.totals").value(0));
        verify(maps).searchSuggestOnMap(argThat(d -> d.getKeyword().equals("公园")));
    }
    @Test void mapLocate() throws Exception {
        var d=new RegionCityDTO(); d.setName("测试城市"); when(maps.getCityByLocation(any())).thenReturn(d);
        ok(json("/map/locate_city_by_location","{\"lat\":31.2,\"lng\":121.5}"))
                .andExpect(jsonPath("$.data.name").value("测试城市")); verify(maps).getCityByLocation(any());
    }
    @ParameterizedTest @ValueSource(strings={"/argument/add","/argument/edit","/dictionary_type/add","/dictionary_type/edit","/dictionary_data/add","/dictionary_data/edit","/sys_user/login/password","/app_user/edit"})
    void invalidWriteBodyRejectedBeforeService(String path) throws Exception {
        mvc.perform(json(path,"{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400000));
        verifyNoInteractions(arguments,dictionaries,sysUsers,appUsers,maps);
    }
    @ParameterizedTest @ValueSource(strings={"/argument/key","/argument/keys","/dictionary_data/type","/dictionary_data/key","/app_user/id_find","/map/region_children_list"})
    void missingQueryRejected(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isBadRequest());
        verifyNoInteractions(arguments,dictionaries,sysUsers,appUsers,maps);
    }
    @Test void backendBusinessFailureIsUnified() throws Exception {
        when(sysUsers.login(any())).thenThrow(new ServiceException("登录失败",400000));
        mvc.perform(json("/sys_user/login/password","{\"phone\":\"13800138000\",\"password\":\"test123\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.msg").value("登录失败"));
    }
}
