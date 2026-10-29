package com.gejia.flashcode.flashcodeportalservice.flash.agent.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.gejia.flashcode.flashcodecommondomain.exception.ServiceException;
import com.gejia.flashcode.flashcodeportalservice.flash.service.IGiteeService;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class CodeCommitAgent implements NodeAction {

    private IGiteeService giteeService;

    public CodeCommitAgent(IGiteeService giteeService) {
        this.giteeService = giteeService;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("--- 开始执行CodeCommitAgent ---");
        Map<String, Object> result = new HashMap<>();
        try {
            Boolean appGenerated = state.value("appGenerated", Boolean.class).orElse(null);
            if (!appGenerated) {
                throw new ServiceException("应用生成失败");
            }
            String appType = state.value("appType", String.class).orElse(null);
            Long appId = state.value("appId", Long.class).orElse(null);
            Path appPath = state.value("appPath", Path.class).orElse(null);
            Map<String, String> files = state.value("files", Map.class).orElse(null);
            giteeService.commit(appId, appPath, appType, files);
            result.put("codeCommit", true);
            result.put("status", "SUCCESS");
        } catch (Exception e) {
            result.put("codeCommit", false);
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
        }
        return result;
    }
}
