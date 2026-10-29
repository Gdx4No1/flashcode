package com.gejia.flashcode.flashcodeadminservice.config.controller;

import com.gejia.flashcode.flashcodeadminapi.config.domain.dto.ArgumentAddReqDTO;
import com.gejia.flashcode.flashcodeadminapi.config.domain.dto.ArgumentDTO;
import com.gejia.flashcode.flashcodeadminapi.config.domain.dto.ArgumentEditReqDTO;
import com.gejia.flashcode.flashcodeadminapi.config.domain.dto.ArgumentListReqDTO;
import com.gejia.flashcode.flashcodeadminapi.config.domain.vo.ArgumentVO;
import com.gejia.flashcode.flashcodeadminapi.config.feign.ArgumentFeignClient;
import com.gejia.flashcode.flashcodeadminservice.config.service.ISysArgumentService;
import com.gejia.flashcode.flashcodecommondomain.domain.R;
import com.gejia.flashcode.flashcodecommondomain.domain.vo.BasePageVO;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 参数服务相关接口
 */
@RestController
@RequestMapping("/argument")
public class ArgumentController implements ArgumentFeignClient {

    @Resource
    private ISysArgumentService iSysArgumentService;

    /**
     * 新增参数
     * @param argumentAddReqDTO 新增参数请求DTO
     * @return Long
     */
    @PostMapping("/add")
    public R<Long> add(@RequestBody @Validated ArgumentAddReqDTO argumentAddReqDTO) {
        return R.ok(iSysArgumentService.add(argumentAddReqDTO));
    }

    /**
     * 参数列表
     * @param argumentListReqDTO 查看参数DTO
     * @return BasePageVO
     */
    @GetMapping("/list")
    public R<BasePageVO<ArgumentVO>> list(@Validated ArgumentListReqDTO argumentListReqDTO) {
        return R.ok(iSysArgumentService.list(argumentListReqDTO));
    }

    /**
     * 编辑参数
     * @param argumentEditReqDTO 编辑参数DTO
     * @return Long
     */
    @PostMapping("/edit")
    public R<Long> edit(@RequestBody @Validated ArgumentEditReqDTO argumentEditReqDTO) {
        return R.ok(iSysArgumentService.edit(argumentEditReqDTO));
    }

    @Override
    public ArgumentDTO getByConfigKey(String configKey) {
        return iSysArgumentService.getByConfigKey(configKey);
    }

    @Override
    public List<ArgumentDTO> getByConfigKeys(List<String> configKeys) {
        return iSysArgumentService.getByConfigKeys(configKeys);
    }
}
