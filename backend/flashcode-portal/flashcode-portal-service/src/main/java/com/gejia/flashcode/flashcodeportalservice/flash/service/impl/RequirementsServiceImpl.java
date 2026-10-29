package com.gejia.flashcode.flashcodeportalservice.flash.service.impl;

import com.gejia.flashcode.flashcodeportalservice.flash.advisor.TokenUsageAdvisor;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.dto.RequirementsDTO;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.entity.App;
import com.gejia.flashcode.flashcodeportalservice.flash.mapper.AppMapper;
import com.gejia.flashcode.flashcodeportalservice.flash.service.IRequirementsService;
import com.gejia.flashcode.flashcodeportalservice.user.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class RequirementsServiceImpl implements IRequirementsService {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private IUserService userService;

    @Autowired
    private AppMapper appMapper;

    @Value("${spring.ai.dashscope.chat.options.model}")
    private String chatModelName;

    @Override
    public RequirementsDTO requirementsGenerate(String input) {
        // 用户id获取
        Long userId = userService.getLoginUser().getUserId();
        App app = new App();
        app.setUserId(userId);
        appMapper.insert(app);
        String conversationId = String.valueOf(app.getId());
        String systemPrompt = composeSystemPrompt();
        String appDoc = chatClient.prompt()
                .system(systemPrompt)
                .user(input)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId)
                        .param(TokenUsageAdvisor.TAG_APP_ID, app.getId())
                        .param(TokenUsageAdvisor.TAG_USER_ID, userId)
                        .param(TokenUsageAdvisor.TAG_MODEL, chatModelName))
                .call()
                .content();

        //应用名称 + 描述 获取
        Map<String, String> parseAppDocResult = parseAppDoc(appDoc);
        String appName = parseAppDocResult.get("appName");
        String appDesc = parseAppDocResult.get("appDesc");

        app.setAppName(appName);
        app.setAppDesc(appDesc);
        app.setAppDoc(appDoc);
        appMapper.updateById(app);

        RequirementsDTO dto = new RequirementsDTO();
        dto.setAppId(app.getId());
        dto.setContent(appDoc);
        return dto;
    }

    private String composeSystemPrompt() {
        return String.join("\n",
                "你是资深产品经理。根据用户提供的需求,生成正式且简洁的应用需求文档.请使用 Markdown 严格排版，采用如下结构与编号：",
                "# 应用需求文档",
                "## 1. 应用名称",
                "## 2. 应用描述",
                "## 3. 应用核心功能",
                "核心功能采用 3.1、3.2… 的编号格式分点呈现；若用户已明确输入核心功能，完全以用户输入内容为准；若用户未提及核心功能，结合用户输入内容生成不超过两个极简功能，所有功能均为列表类功能，所有功能均独立实现，不依赖任何第三方服务。需求文档中不提及任何第三方服务或平台。",
                "注意：仅输出符合以上要求的需求文档正文，不要任何的附加说明或多余内容");
    }

    /**
     * 从AI返回的Markdown内容中解析应用信息（应用名称和应用描述）
     *
     * @param content AI返回的Markdown格式内容
     * @return 包含应用名称(appName)和应用描述(appDesc)的Map
     */
    private Map<String, String> parseAppDoc(String content) {
        Map<String, String> result = new HashMap<>();

        if (content == null || content.isEmpty()) {
            log.warn("解析应用信息失败：内容为空");
            result.put("appName", "");
            result.put("appDesc", "");
            return result;
        }

        // 解析应用名称：匹配 "## 1. 应用名称" 之后到下一个 "##" 之前的内容
        String appName = parseSection(content, 1, "应用名称");
        result.put("appName", appName);
        log.debug("成功解析应用名称: {}", appName);

        // 解析应用描述：匹配 "## 2. 应用描述" 之后到下一个 "##" 之前的内容
        String appDesc = parseSection(content, 2, "应用描述");
        result.put("appDesc", appDesc);
        log.debug("成功解析应用描述: {}", appDesc);

        return result;
    }

    /**
     * 从Markdown内容中解析指定章节的内容
     *
     * @param content Markdown格式内容
     * @param sectionNumber 章节编号
     * @param sectionName 章节名称
     * @return 章节内容
     */
    private String parseSection(String content, int sectionNumber, String sectionName) {
        String regex = String.format("##\\s*%d\\.\\s*%s\\s*[\\r\\n]+([^#]+?)(?=##|$)", sectionNumber, sectionName);
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        log.warn("未能从内容中解析出{}", sectionName);
        return "";
    }
}
