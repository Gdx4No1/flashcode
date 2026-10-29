package com.gejia.flashcode.flashcodeportalservice.flash.agent.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gejia.flashcode.flashcodecommondomain.domain.R;
import com.gejia.flashcode.flashcodecommondomain.domain.ResultCode;
import com.gejia.flashcode.flashcodecommondomain.exception.ServiceException;
import com.gejia.flashcode.flashcodefileapi.domin.vo.FileVO;
import com.gejia.flashcode.flashcodefileapi.feign.FileFeignClient;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.entity.App;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.entity.SimpleMultipartFile;
import com.gejia.flashcode.flashcodeportalservice.flash.eums.AppType;
import com.gejia.flashcode.flashcodeportalservice.flash.mapper.AppMapper;
import com.gejia.flashcode.flashcodeportalservice.flash.utils.SeleniumUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class AppScreenshotAgent implements NodeAction {

    private FileFeignClient fileFeignClient;

    private AppMapper appMapper;

    public AppScreenshotAgent(FileFeignClient fileFeignClient, AppMapper appMapper) {
        this.fileFeignClient = fileFeignClient;
        this.appMapper = appMapper;
    }

    //借助Selenium完成应用截图
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("--- 开始执行AppScreenshotAgent ---");
        Map<String, Object> result = new HashMap<>();
        try {
            Long appId = state.value("appId", Long.class).orElse(null);
            Boolean buildPreview = state.value("buildPreview", Boolean.class).orElse(null);
            if (!buildPreview) {
                throw new ServiceException("编译构建失败");
            }
            String previewUrl = state.value("previewUrl", String.class).orElse(null);
            //screenshotPath 截图并压缩后的图片地址
            String screenshotPath = SeleniumUtil.screenshot(previewUrl, SeleniumUtil.DEFAULT_COMPRESSION_QUALITY);
            MultipartFile file = new SimpleMultipartFile(
                    "AppHome",
                    new File(screenshotPath).getName(),
                    "image/jpeg",
                    new FileInputStream(screenshotPath)
            );
            R<FileVO> uploadResult = fileFeignClient.upload(file);
            if (uploadResult == null || uploadResult.getCode() != ResultCode.SUCCESS.getCode() || uploadResult.getData() == null) {
                throw new ServiceException("应用截图上传失败");
            }
            String ossUrl = uploadResult.getData().getUrl();
            log.info("--- 文件上传成功: {}---", uploadResult.getData());
            Files.deleteIfExists(Paths.get(screenshotPath)); //删除临时文件
            appMapper.update(new LambdaUpdateWrapper<App>()
                    .eq(App::getId, appId)
                    .set(App::getAppScreenshot, ossUrl));
            result.put("appScreenshot", true);
            result.put("status", "SUCCESS");
        } catch (Exception e) {
            result.put("appScreenshot", false);
            result.put("error", e.getMessage());
            result.put("status", "FAILED");
        }
        return result;
    }
}
