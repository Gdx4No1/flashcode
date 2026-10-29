package com.gejia.flashcode.flashcodeportalservice.flash.utils;

import com.gejia.flashcode.flashcodeportalservice.flash.eums.AppType;
import lombok.Data;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModelOutputParser {

    @Data
    public static class ParsedResult {
        private String appType;

        private Map<String, String> files = new LinkedHashMap<>();

    }

    public static ParsedResult parse(String output) {
        ParsedResult result = new ParsedResult();
        if (output == null) {
            return result;
        }

        // 移除 <think>...</think> 标签及其内容
        output = output.replaceAll("(?s)<think>.*?</think>", "");

        List<String> lines = Arrays.asList(output.split("\r?\n"));

        // Parse FILE blocks first
        String currentPath = null;
        StringBuilder buf = null;
        boolean inFence = false;
        for (String line : lines) {
            String t = line;
            if (t.startsWith("FILE:")) {
                // flush previous
                if (currentPath != null && buf != null) {
                    result.files.put(currentPath, buf.toString());
                }
                currentPath = t.substring("FILE:".length()).trim();
                buf = new StringBuilder();
                inFence = false;
                continue;
            }
            if (t.startsWith("```")) {
                // toggle fence, but do not include fence lines
                inFence = !inFence;
                continue;
            }
            if (inFence && buf != null) {
                buf.append(line).append("\n");
            }
        }
        // flush last
        if (currentPath != null && buf != null) {
            result.files.put(currentPath, buf.toString());
        }

        // Parse APP_TYPE based on file analysis
        result.appType = determineAppType(result.files);

        return result;
    }
    //        - HTML：整个应用只有一个HTML页面，不依赖任何后端服务。     仅包含一个文件，并且文件的后缀是.html
//        - VUE：基于 Vue3 构建的纯前端应用，不依赖后端服务。       只包含.vue  不包含.java
//        - VUE_SPRING：完整的前后端分离应用，前端基于 Vue3 架构，后端采用 SpringBoot 框架。  既包含.vue文件又包含.java文件
    private static String determineAppType(Map<String, String> files) {
        if (files == null || files.isEmpty()) {
            return null;
        }

        // 规则1: 如果仅生成了一个文件并且文件后缀为.html，则应用类型为HTML
        if (files.size() == 1) {
            String singleFile = files.keySet().iterator().next();
            if (singleFile.toLowerCase().endsWith(".html")) {
                return AppType.HTML.getType();
            }
        }

        // 规则2: 如果生成的文件同时包含.java文件和.vue文件，则应用类型为VUE_SPRING
        boolean hasJavaFile = files.keySet().stream()
                .anyMatch(path -> path.toLowerCase().endsWith(".java"));
        boolean hasVueFile = files.keySet().stream()
                .anyMatch(path -> path.toLowerCase().endsWith(".vue"));
        if (hasJavaFile && hasVueFile) {
            return AppType.VUE_SPRING.getType();
        }

        // 规则3: 如果既不是HTML类型也不是VUE_SPRING类型，并且生成的文件中包含.vue文件，则应用类型为VUE
        if (hasVueFile) {
            return AppType.VUE.getType();
        }

        return "error";
    }
}
