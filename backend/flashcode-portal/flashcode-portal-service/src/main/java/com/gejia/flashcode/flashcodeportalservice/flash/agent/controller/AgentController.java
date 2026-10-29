package com.gejia.flashcode.flashcodeportalservice.flash.agent.controller;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.gejia.flashcode.flashcodecommondomain.domain.R;
import com.gejia.flashcode.flashcodecommondomain.exception.ServiceException;
import com.gejia.flashcode.flashcodeportalservice.flash.agent.MultiAgentWorkflow;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.vo.GenerateAppResVO;
import com.gejia.flashcode.flashcodeportalservice.flash.utils.FileUtil;
import com.gejia.flashcode.flashcodeportalservice.user.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/agent/flash")
public class AgentController {

    @Autowired
    private MultiAgentWorkflow workflow;

    @Value("${spring.ai.dashscope.chat.options.model}")
    private String chatModelName;

    @Autowired
    private IUserService userService;

    //1. 逐个完成每个agent的业务逻辑      2. 组织多个agent，使得多个agent协同完成工作

    @PostMapping("/apps/generate")
    R<GenerateAppResVO> appGenerate(@RequestParam Long appId,
                                    @RequestParam String prompt,
                                    @RequestPart(value = "sketch", required = false)MultipartFile sketch){
        //启动多agent工作流    初始化state
        Map<String, Object> initStateData = new HashMap<>();
        initStateData.put("appId", appId);
        initStateData.put("appDoc", prompt);
        initStateData.put("userId", userService.getLoginUser().getUserId());
        initStateData.put("chatModelName", chatModelName);
        if (sketch != null) {
            String sketchPath = FileUtil.saveFile(sketch, String.valueOf(appId));  //将文件存储到本地
            initStateData.put("sketch", sketchPath);
            initStateData.put("contentType", sketch.getContentType());
        }
        OverAllState overAllState = new OverAllState(initStateData);
        OverAllState result = workflow.execute(overAllState);
        return R.ok(convert2TO(appId, result));
    }

    private GenerateAppResVO convert2TO(Long appId, OverAllState result) {
        String status = result.value("status", String.class).orElse(null);
        if (!"SUCCESS".equals(status)) {
            String errorMsg = result.value("error", String.class).orElse(null);
            throw new ServiceException("工作流执行失败：" + errorMsg);
        }
        String appType = result.value("appType", String.class).orElse(null);
        String previewUrl = result.value("previewUrl", String.class).orElse(null);
        return new GenerateAppResVO(appId, appType, previewUrl);
    }
}
