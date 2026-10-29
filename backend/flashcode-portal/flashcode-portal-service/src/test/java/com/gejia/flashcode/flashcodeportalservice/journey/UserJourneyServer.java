package com.gejia.flashcode.flashcodeportalservice.journey;

import com.gejia.flashcode.flashcodeportalservice.flash.controller.*;
import com.gejia.flashcode.flashcodeportalservice.flash.service.*;
import com.gejia.flashcode.flashcodeportalservice.flash.service.impl.*;
import com.gejia.flashcode.flashcodeportalservice.flash.config.ChatClientConfig;
import com.gejia.flashcode.flashcodeportalservice.flash.config.RequirementUploadConfig;
import com.gejia.flashcode.flashcodeportalservice.flash.utils.AppTaskCoordinator;
import com.gejia.flashcode.flashcodeportalservice.user.controller.UserController;
import com.gejia.flashcode.flashcodeportalservice.user.service.impl.IUserServiceImpl;
import com.gejia.flashcode.flashcodecommonsecurity.service.TokenService;
import com.gejia.flashcode.flashcodecommonsecurity.handler.GlobalExceptionHandler;
import com.gejia.flashcode.flashcodecommonsecurity.utils.JwtUtil;
import com.gejia.flashcode.flashcodecommonredis.service.RedisService;
import com.gejia.flashcode.flashcodecommonmessage.service.*;
import com.gejia.flashcode.flashcodeadminapi.appuser.feign.AppUserFeignClient;
import com.gejia.flashcode.flashcodeadminapi.appuser.domain.vo.AppUserVo;
import com.gejia.flashcode.flashcodecommondomain.domain.R;
import com.github.dockerjava.api.DockerClient;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.yaml.snakeyaml.Yaml;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 只能从 test classpath 启动；无现用数据库地址，不会进入生产 JAR。 */
@Configuration
@EnableAutoConfiguration
@MapperScan("com.gejia.flashcode.flashcodeportalservice.flash.mapper")
@Import({FlashcodeController.class,AppController.class,ChatHistoryController.class,UserController.class,
    AppGenerationServiceImpl.class,RequirementsServiceImpl.class,AppAccessService.class,AppQueryService.class,
    ChatHistoryService.class,AppReleaseService.class,AppTaskCoordinator.class,ChatClientConfig.class,RequirementUploadConfig.class,
    IUserServiceImpl.class,TokenService.class,CaptchaService.class,GlobalExceptionHandler.class})
public class UserJourneyServer {
    public static final String ROOT=System.getProperty("flashcode.journey.root", "D:/tmp/flashcode-build/user-journey");
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        System.setProperty("spring.autoconfigure.exclude","org.redisson.spring.starter.RedissonAutoConfigurationV2");
        Files.createDirectories(Path.of(ROOT));
        System.setProperty("user.dir", ROOT);
        JwtUtil.secret=UUID.randomUUID().toString()+UUID.randomUUID();
        Map<String,Object> config=new Yaml().load(Files.readString(Path.of("D:/tmp/flashcode-build/local-runtime/portal.yaml")));
        Map<String,Object> spring=(Map<String,Object>)config.get("spring");
        Map<String,Object> ai=(Map<String,Object>)spring.get("ai");
        Map<String,Object> openai=(Map<String,Object>)ai.get("openai");
        Map<String,Object> deepseek=(Map<String,Object>)ai.get("deepseek");
        System.setProperty("spring.ai.openai.api-key",String.valueOf(openai.get("api-key")));
        System.setProperty("spring.ai.openai.base-url",String.valueOf(openai.get("base-url")));
        System.setProperty("spring.ai.deepseek.model",String.valueOf(deepseek.get("model")));
        SpringApplication app=new SpringApplication(UserJourneyServer.class);
        app.setWebApplicationType(WebApplicationType.SERVLET);
        app.setDefaultProperties(Map.ofEntries(
            Map.entry("server.address","127.0.0.1"),Map.entry("server.port","18483"),
            Map.entry("spring.datasource.url","jdbc:h2:mem:userjourney;MODE=MySQL;DB_CLOSE_DELAY=-1"),
            Map.entry("spring.datasource.driver-class-name","org.h2.Driver"),Map.entry("spring.datasource.username","sa"),
            Map.entry("spring.datasource.password",""),Map.entry("spring.sql.init.mode","never"),
            Map.entry("spring.servlet.multipart.max-file-size","5MB"),Map.entry("spring.servlet.multipart.max-request-size","6MB"),
            Map.entry("app.preview.host","http://127.0.0.1:5178"),Map.entry("app.preview.local-root",ROOT+"/preview"),
            Map.entry("app.deploy.host","http://127.0.0.1:5179"),Map.entry("app.deploy.local-root",ROOT+"/release"),
            Map.entry("app.features.chat-history","true"),Map.entry("app.features.extended-schema","true"),
            Map.entry("sms.send-limit","20"),Map.entry("sms.code-expiration","5"),Map.entry("sms.send-message","false"),
            Map.entry("logging.level.root","WARN"),Map.entry("spring.ai.openai.chat.options.max-tokens","8192"),
            Map.entry("spring.cloud.nacos.discovery.enabled","false"),Map.entry("spring.cloud.nacos.config.enabled","false"),
            Map.entry("spring.cloud.discovery.enabled","false"),Map.entry("spring.cloud.compatibility-verifier.enabled","false")
        ));
        app.run(args);
        System.out.println("USER_JOURNEY_READY http://127.0.0.1:18483 (isolated H2, live model, no production DB)");
    }
    @Bean DockerClient dockerClient() { return mock(DockerClient.class); }
    @Bean org.redisson.api.RedissonClient redissonClient() { return mock(org.redisson.api.RedissonClient.class); }
    @Bean org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory() { return mock(org.springframework.data.redis.connection.RedisConnectionFactory.class); }
    @Bean @Primary com.aliyun.dysmsapi20170525.Client smsClient() { return mock(com.aliyun.dysmsapi20170525.Client.class); }
    @Bean GiteeService giteeService() { return mock(GiteeService.class); }
    @Bean AliSmsService aliSmsService() { return mock(AliSmsService.class); }
    @Bean RedisService redisService() {
        var redis=mock(RedisService.class);
        Map<String,Object> values=new ConcurrentHashMap<>(); Map<String,Long> expiry=new ConcurrentHashMap<>();
        when(redis.getCacheObject(anyString(),any(Class.class))).thenAnswer(call->{
            String key=call.getArgument(0); return expiry.getOrDefault(key,0L)>System.currentTimeMillis() ? values.get(key) : null;
        });
        doAnswer(call->{String key=call.getArgument(0); values.put(key,call.getArgument(1));
            long ttl=call.getArgument(2); TimeUnit unit=call.getArgument(3); expiry.put(key,System.currentTimeMillis()+unit.toMillis(ttl));return null;
        }).when(redis).setCacheObject(anyString(),any(),anyLong(),any(TimeUnit.class));
        when(redis.getExpire(anyString())).thenAnswer(call->Math.max(-2L,(expiry.getOrDefault(call.getArgument(0),0L)-System.currentTimeMillis())/1000));
        when(redis.deleteObject(anyString())).thenAnswer(call->{String key=call.getArgument(0); expiry.remove(key);return values.remove(key)!=null;});
        return redis;
    }
    @Bean AppUserFeignClient appUserFeignClient() {
        var client=mock(AppUserFeignClient.class); Map<String,AppUserVo> users=new ConcurrentHashMap<>(); AtomicLong ids=new AtomicLong(7);
        when(client.findByPhone(anyString())).thenAnswer(call->R.ok(users.get(call.getArgument(0))));
        when(client.registerByPhone(anyString())).thenAnswer(call->{String phone=call.getArgument(0); AppUserVo user=new AppUserVo();
            user.setUserId(ids.getAndIncrement()); user.setPhoneNumber(phone); user.setNickName("用户旅程测试"); users.put(phone,user);return R.ok(user);});
        when(client.findById(anyLong())).thenAnswer(call->{Long id=call.getArgument(0);return R.ok(users.values().stream().filter(u->u.getUserId().equals(id)).findFirst().orElse(null));});
        return client;
    }
    @Bean InitializingBean database(JdbcTemplate jdbc) { return ()->{
        jdbc.execute("CREATE TABLE app(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT,app_name VARCHAR(100),app_desc VARCHAR(2000),app_type INT,app_doc CLOB,app_screenshot VARCHAR(2048),preview_url VARCHAR(2048),deploy_status INT DEFAULT 0,app_url VARCHAR(2048),create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP,deploy_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE chat_history(id BIGINT AUTO_INCREMENT PRIMARY KEY,app_id BIGINT,msg_role INT,content CLOB,chat_time TIMESTAMP)");
        jdbc.execute("CREATE INDEX idx_chat_history_app_id_id ON chat_history(app_id,id)");
        jdbc.update("INSERT INTO app(id,user_id,app_name,app_desc,app_type,app_doc) VALUES(999,99,'他人私有应用','权限边界测试',0,'不允许读取的文档')");
        if (Boolean.getBoolean("flashcode.journey.pagination-fixtures")) {
            for (long id=900;id<906;id++) jdbc.update("INSERT INTO app(id,user_id,app_name,app_desc,app_type,app_doc,deploy_status,app_url) VALUES(?,99,?,'隔离分页样例',0,'测试数据',1,?)",id,"分页样例"+id,"http://127.0.0.1:5179/"+id+"/");
        }
    }; }
    /** 测试网关边界替身；实际网关须另行检查，不能将此过滤器视为其验收。 */
    @Bean FilterRegistrationBean<Filter> isolatedAuth(TokenService tokens) {
        Filter filter=(request,response,chain)->{
            var req=(HttpServletRequest)request;var res=(HttpServletResponse)response;String uri=req.getRequestURI();
            boolean publicPath=uri.startsWith("/user/login/")||uri.equals("/user/send_code")||uri.equals("/app/list");
            if(!publicPath && tokens.getLoginUser(req)==null) {res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"code\":401000,\"msg\":\"请先登录\"}");return;}
            chain.doFilter(request,response);
        };
        var registration=new FilterRegistrationBean<>(filter);registration.setOrder(-100);return registration;
    }
}
