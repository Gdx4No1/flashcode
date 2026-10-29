package com.gejia.flashcode.flashcodeportalservice.flash.service;

import java.nio.file.Path;
import java.util.Map;

public interface IGiteeService {
    void commit(Long appId, Path appPath, String appType, Map<String, String> files);

    void pullUserAppCode(Long appId, Path userCodeBaseDir);

    void delete(Long appId);
}
