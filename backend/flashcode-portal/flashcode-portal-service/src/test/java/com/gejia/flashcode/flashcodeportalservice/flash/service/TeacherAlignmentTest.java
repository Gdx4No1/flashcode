package com.gejia.flashcode.flashcodeportalservice.flash.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.entity.*;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.dto.*;
import com.gejia.flashcode.flashcodeportalservice.flash.mapper.*;
import com.gejia.flashcode.flashcodeportalservice.flash.service.impl.AppGenerationServiceImpl;
import com.gejia.flashcode.flashcodeportalservice.flash.utils.*;
import com.gejia.flashcode.flashcodeportalservice.user.entity.dto.UserDTO;
import com.gejia.flashcode.flashcodeportalservice.user.service.IUserService;
import com.gejia.flashcode.flashcodecommondomain.exception.ServiceException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 真正执行文件编辑/发布快照与服务权限逻辑；模型和数据库是隔离替身。 */
@ResourceLock("user.dir")
class TeacherAlignmentTest {
    @TempDir Path directory;
    AppMapper mapper; IUserService users; ChatModel model; AppAccessService access;
    AppGenerationServiceImpl generation; AppReleaseMapper releaseMapper; AppReleaseService releases;
    ChatHistoryMapper historyMapper; ChatHistoryService history; App app; String previous;
    @BeforeEach void setup() {
        previous = System.getProperty("user.dir"); System.setProperty("user.dir", directory.toString());
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "alignment-test"), App.class);
        mapper=mock(AppMapper.class); users=mock(IUserService.class); model=mock(ChatModel.class);
        UserDTO user=new UserDTO(); user.setUserId(7L); when(users.getLoginUser()).thenReturn(user);
        app=new App(); app.setId(42L); app.setUserId(7L); app.setAppType(0); app.setAppDoc("原需求");
        when(mapper.selectById(42L)).thenReturn(app); when(mapper.update(any(Wrapper.class))).thenReturn(1);
        releaseMapper=mock(AppReleaseMapper.class); releases=new AppReleaseService(releaseMapper);
        access=new AppAccessService(mapper, users); ReflectionTestUtils.setField(access,"releases",releases);
        historyMapper=mock(ChatHistoryMapper.class); history=new ChatHistoryService(historyMapper, access);
        generation=new AppGenerationServiceImpl(ChatClient.builder(model).build());
        ReflectionTestUtils.setField(generation,"appMapper",mapper); ReflectionTestUtils.setField(generation,"userService",users);
        ReflectionTestUtils.setField(generation,"access",access); ReflectionTestUtils.setField(generation,"history",history);
        ReflectionTestUtils.setField(generation,"releases",releases);
        ReflectionTestUtils.setField(generation,"previewHost","https://preview.test");
        ReflectionTestUtils.setField(generation,"previewLocalRoot",directory.resolve("preview").toString());
        ReflectionTestUtils.setField(generation,"deployLocalRoot",directory.resolve("release").toString());
        ReflectionTestUtils.setField(generation,"deployHost","https://release.test");
    }
    @AfterEach void cleanup() { System.setProperty("user.dir",previous); }
    Path source() throws Exception { return Files.createDirectories(SourceWorkspace.root(42L)); }
    void response(String text) { when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text))))); }
    AppEditDTO edit() { var dto=new AppEditDTO(); dto.setAppId(42L); dto.setNewContent("修改标题"); return dto; }
    @Test void oldAppMappingDoesNotQueryNewColumns() {
        var info=TableInfoHelper.getTableInfo(App.class);
        assertFalse(info.getAllSqlSelect().contains("deploy_status"));
        assertFalse(info.getAllSqlSelect().contains("app_url"));
        assertFalse(info.getAllSqlSelect().contains("create_time"));
    }
    @Test void disabledReleaseNeverTouchesDatabase() { releases.hydrate(app); assertThrows(ServiceException.class, releases::requireEnabled); verifyNoInteractions(releaseMapper); }
    @Test void disabledHistoryNeverTouchesNewTable() {
        history.append(42L,0,"消息"); var req=new ChatHistoryListReqDTO(); req.setAppId(42L);
        assertTrue(history.list(req).getList().isEmpty()); assertTrue(history.recent(42L).isEmpty()); verifyNoInteractions(historyMapper);
    }
    @Test void disabledPublishedListDoesNotQueryMissingColumn() {
        var queries=new AppQueryService(mapper,users); ReflectionTestUtils.setField(queries,"releases",releases);
        assertTrue(queries.publishedList(new MyAppListReqDTO()).getList().isEmpty()); verifyNoInteractions(mapper,releaseMapper);
    }
    @Test void sourceEditPreservesUnchangedFilesAndAssets() throws Exception {
        Path source=source(); Files.writeString(source.resolve("index.html"),"<html>old</html>");
        Files.writeString(source.resolve("style.css"),"body{color:red}"); Files.write(source.resolve("image.png"), new byte[]{1,2,3});
        response("APP_TYPE=HTML\nFILE: index.html\n```html\n<html>new</html>\n```");
        assertEquals("https://preview.test/42/",generation.appEdit(edit()).getPreviewUrl());
        assertTrue(Files.readString(source.resolve("index.html")).contains("new"));
        assertEquals("body{color:red}",Files.readString(source.resolve("style.css")));
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(source.resolve("image.png")));
        assertTrue(Files.readString(directory.resolve("preview/42/index.html")).contains("new"));
        verifyNoInteractions(historyMapper,releaseMapper);
    }
    @Test void incompleteResponseDoesNotReplaceSourceOrPreview() throws Exception {
        Path source=source(); Files.writeString(source.resolve("index.html"),"original");
        Path preview=Files.createDirectories(directory.resolve("preview/42")); Files.writeString(preview.resolve("index.html"),"preview-original");
        response("FILE: index.html\n```html\nbroken");
        assertThrows(ServiceException.class,()->generation.appEdit(edit()));
        assertEquals("original",Files.readString(source.resolve("index.html")));
        assertEquals("preview-original",Files.readString(preview.resolve("index.html")));
        verify(mapper,never()).update(any(Wrapper.class));
    }
    @Test void traversalResponseCannotWriteOutsideWorkspace() throws Exception {
        Files.writeString(source().resolve("index.html"),"original");
        response("FILE: ../escaped.txt\n```text\nescape\n```");
        assertThrows(ServiceException.class,()->generation.appEdit(edit()));
        assertFalse(Files.exists(directory.resolve("user-code/escaped.txt")));
        assertEquals("original",Files.readString(source().resolve("index.html")));
    }
    @Test void anotherUserCannotUseAnyEditingOrPublishingOperation() {
        app.setUserId(99L);
        assertThrows(ServiceException.class,()->generation.appEdit(edit()));
        assertThrows(ServiceException.class,()->generation.completeEdit(42L));
        assertThrows(ServiceException.class,()->generation.vsCodeEdit(42L));
        assertThrows(ServiceException.class,()->generation.deployApp(42L));
        verify(model,never()).call(any(Prompt.class)); verifyNoInteractions(releaseMapper,historyMapper);
    }
    @Test void missingEditorConfigurationIsAnExplicitFailure() { assertThrows(ServiceException.class,()->generation.vsCodeEdit(42L)); }
    @Test void editorUrlPointsToCorrectApplicationWorkspace() throws Exception {
        Files.writeString(source().resolve("index.html"),"source");
        ReflectionTestUtils.setField(generation,"editorUrl","https://editor.test/");
        ReflectionTestUtils.setField(generation,"editorWorkspaceRoot","/home/workspace");
        assertEquals("https://editor.test/?folder=%2Fhome%2Fworkspace%2F42",generation.vsCodeEdit(42L));
    }
    @Test void completeEditBuildsExistingHtml() throws Exception {
        Files.writeString(source().resolve("index.html"),"<html>edited</html>"); assertTrue(generation.completeEdit(42L));
        assertTrue(Files.readString(directory.resolve("preview/42/index.html")).contains("edited")); verify(model,never()).call(any(Prompt.class));
    }
    @Test void disabledDeploymentDoesNotCreateReleaseFiles() {
        assertThrows(ServiceException.class,()->generation.deployApp(42L)); assertFalse(Files.exists(directory.resolve("release"))); verifyNoInteractions(releaseMapper);
    }
    @Test void staticDeploymentCreatesIndependentSnapshot() throws Exception {
        ReflectionTestUtils.setField(releases,"enabled",true); when(releaseMapper.updateById(any(AppRelease.class))).thenReturn(1);
        Path preview=Files.createDirectories(directory.resolve("preview/42")); Files.writeString(preview.resolve("index.html"),"published-v1");
        String url=generation.deployApp(42L); assertTrue(url.startsWith("https://release.test/42/"));
        Path snapshot=directory.resolve("release").resolve(url.substring("https://release.test/".length())).resolve("index.html");
        Files.writeString(preview.resolve("index.html"),"preview-v2");
        assertEquals("published-v1",Files.readString(snapshot));
        String newer=generation.deployApp(42L); assertNotEquals(url,newer);
        assertEquals("published-v1",Files.readString(snapshot));
        assertEquals("preview-v2",Files.readString(directory.resolve("release").resolve(newer.substring("https://release.test/".length())).resolve("index.html")));
    }
    @Test void failedReleaseMetadataCannotOverwritePreviousSnapshot() throws Exception {
        ReflectionTestUtils.setField(releases,"enabled",true); when(releaseMapper.updateById(any(AppRelease.class))).thenReturn(1);
        Path preview=Files.createDirectories(directory.resolve("preview/42")); Files.writeString(preview.resolve("index.html"),"published-v1");
        String url=generation.deployApp(42L);
        Files.writeString(preview.resolve("index.html"),"candidate-v2"); when(releaseMapper.updateById(any(AppRelease.class))).thenReturn(0);
        assertThrows(ServiceException.class,()->generation.deployApp(42L));
        assertEquals("published-v1",Files.readString(directory.resolve("release").resolve(url.substring("https://release.test/".length())).resolve("index.html")));
    }
    @Test void missingPreviewCannotMarkApplicationPublished() {
        ReflectionTestUtils.setField(releases,"enabled",true);
        assertThrows(ServiceException.class,()->generation.deployApp(42L));
        verify(releaseMapper,never()).updateById(any(AppRelease.class));
    }
    @Test void fullStackCannotBePublishedAsStaticFiles() {
        ReflectionTestUtils.setField(releases,"enabled",true); app.setAppType(2);
        assertThrows(ServiceException.class,()->generation.deployApp(42L));
        verify(releaseMapper,never()).updateById(any(AppRelease.class));
    }
    @Test void taskLockRejectsConcurrentChangesAndReleasesAfterClose() {
        var tasks=new AppTaskCoordinator(); try(var lease=tasks.acquire(42L)) { assertThrows(ServiceException.class,()->tasks.acquire(42L)); }
        try(var lease=tasks.acquire(42L)) { assertNotNull(lease); }
    }
    @Test void sourceCollectionPrunesDependencyAndBuildFolders() throws Exception {
        Path source=source(); Files.writeString(source.resolve("index.html"),"source");
        Files.createDirectories(source.resolve("node_modules/lib")); Files.writeString(source.resolve("node_modules/lib/big.js"),"x".repeat(300000));
        Files.createDirectories(source.resolve(".git")); Files.writeString(source.resolve(".git/config"),"private");
        assertEquals(Set.of("index.html"),SourceWorkspace.read(source).keySet());
    }
    @Test void enabledHistoryReturnsChronologicalPageAndExactStringIds() {
        ReflectionTestUtils.setField(history,"enabled",true);
        when(historyMapper.selectCount(any())).thenReturn(3L);
        ChatHistory newer=new ChatHistory(); newer.setId(9007199254740993L); newer.setAppId(42L); newer.setMsgRole(1); newer.setContent("回复");
        ChatHistory older=new ChatHistory(); older.setId(9007199254740992L); older.setAppId(42L); older.setMsgRole(0); older.setContent("请求");
        when(historyMapper.selectList(any())).thenReturn(List.of(newer,older));
        var req=new ChatHistoryListReqDTO(); req.setAppId(42L); req.setPageSize(2);
        var page=history.list(req); assertEquals(3,page.getTotals()); assertEquals(2,page.getTotalPages());
        assertEquals("9007199254740992",page.getList().getFirst().getId()); assertEquals("请求",page.getList().getFirst().getContent());
        assertEquals("9007199254740993",page.getList().getLast().getId());
    }
    @Test void publishedDetailIsReadOnlyAndNeverExposesPrivateDocument() {
        app.setUserId(99L); app.setDeployStatus(1); app.setAppUrl("https://release.test/42/"); app.setPreviewUrl("https://private-preview.test/42/");
        var detail=new AppQueryService(mapper,users).detail(42L);
        assertFalse(detail.isCanEdit()); assertNull(detail.getAppDoc()); assertEquals(app.getAppUrl(),detail.getPreviewUrl());
    }
    @Test void anotherUserCannotReadEvenDisabledPrivateHistory() {
        app.setUserId(99L); var req=new ChatHistoryListReqDTO(); req.setAppId(42L);
        assertThrows(ServiceException.class,()->history.list(req)); verifyNoInteractions(historyMapper);
    }
    @Test void publicDetailRequiresEnabledPublishingAndDoesNotReadLoginOrPrivateData() {
        var queries=new AppQueryService(mapper,users); ReflectionTestUtils.setField(queries,"releases",releases);
        clearInvocations(mapper,users);
        assertThrows(ServiceException.class,()->queries.publishedDetail(42L)); verifyNoInteractions(mapper,users);
        ReflectionTestUtils.setField(releases,"enabled",true);
        var release=new AppRelease(); release.setDeployStatus(1); release.setAppUrl("https://release.test/42/v1/");
        when(releaseMapper.selectById(42L)).thenReturn(release);
        var detail=queries.publishedDetail(42L);
        assertNull(detail.getAppDoc()); assertEquals(release.getAppUrl(),detail.getPreviewUrl());
        assertFalse(detail.isCanEdit()); assertFalse(detail.isDeploymentEnabled()); assertFalse(detail.isChatHistoryEnabled());
        verifyNoInteractions(users);
    }
    @Test void publicDetailReturnsSameFailureForMissingAndUnpublishedApplications() {
        var queries=new AppQueryService(mapper,users); ReflectionTestUtils.setField(queries,"releases",releases);
        ReflectionTestUtils.setField(releases,"enabled",true);
        var privateError=assertThrows(ServiceException.class,()->queries.publishedDetail(42L));
        when(mapper.selectById(42L)).thenReturn(null);
        var missingError=assertThrows(ServiceException.class,()->queries.publishedDetail(42L));
        assertEquals(privateError.getCode(),missingError.getCode()); assertEquals(privateError.getMessage(),missingError.getMessage());
        assertThrows(ServiceException.class,()->queries.publishedDetail(-1L));
    }
    @Test void sourceEditorReadsOnlyOwnedAllowedFilesAndSavesExistingFile() throws Exception {
        Path source=source(); Files.writeString(source.resolve("index.html"),"old");
        Files.writeString(source.resolve("script.js"),"console.log(1)"); Files.writeString(source.resolve("secret.bin"),"private");
        assertEquals(Set.of("index.html","script.js"),generation.sourceFiles(42L).keySet());
        var dto=new com.gejia.flashcode.flashcodeportalservice.flash.domain.dto.SourceFileSaveDTO(); dto.setAppId(42L);dto.setPath("index.html");dto.setContent("new");
        generation.saveSourceFile(dto); assertEquals("new",Files.readString(source.resolve("index.html")));
    }
    @Test void sourceEditorRejectsTraversalAndOtherUser() throws Exception {
        Path source=source(); Files.writeString(source.resolve("index.html"),"old");
        var traversal=new com.gejia.flashcode.flashcodeportalservice.flash.domain.dto.SourceFileSaveDTO(); traversal.setAppId(42L);traversal.setPath("../escape.html");traversal.setContent("escape");
        assertThrows(ServiceException.class,()->generation.saveSourceFile(traversal)); assertFalse(Files.exists(directory.resolve("escape.html")));
        app.setUserId(99L); assertThrows(ServiceException.class,()->generation.sourceFiles(42L)); assertThrows(ServiceException.class,()->generation.saveSourceFile(traversal));
    }
}
