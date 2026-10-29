package com.gejia.flashcode.flashcodeportalservice.flash.controller;

import com.gejia.flashcode.flashcodecommondomain.domain.R;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.dto.AppEditDTO;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.dto.AppReqDTO;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.vo.GenerateAppResVO;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.vo.RequirementsVO;
import com.gejia.flashcode.flashcodeportalservice.flash.service.IAppGenerationService;
import com.gejia.flashcode.flashcodeportalservice.flash.service.IRequirementsService;
import com.gejia.flashcode.flashcodeportalservice.flash.service.ITestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequestMapping("/flash")
public class FlashcodeController {

    @Autowired
    private IRequirementsService requirementsService;

    @Autowired
    private IAppGenerationService appGenerationService;

    @Autowired
    private ITestService testService;

    //url ok  请求方法 ok  请求参数 ok  响应数据结构 ok
    //get  post  put  delete
    @PostMapping("/requirements/generate")
    R<RequirementsVO> requirementsGenerate(@RequestParam String input) {
        return R.ok(requirementsService.requirementsGenerate(input).convertToVO());
    }

//    /flash/apps/generate
    @PostMapping("/apps/generate")
    R<GenerateAppResVO> appGenerate(@RequestParam Long appId, @RequestParam String appDoc){
        return R.ok(appGenerationService.appGenerate(appId, appDoc).convertToVO());
    }

    @PostMapping("test/chat")
    public R<String> testChat(@RequestParam String prompt, @RequestParam String conversationId) {
        return R.ok(testService.testChat(prompt, conversationId));
    }

    //url  ok  请求方法 ok  请求参数 ok  响应数据结构 ok
    @PostMapping("/apps/edit")
    public R<GenerateAppResVO> appEdit(@RequestBody AppEditDTO appEditDTO) {
        return R.ok(appGenerationService.appEdit(appEditDTO).convertToVO());
    }

    //url ok  请求方法  ok  请求参数 ok   响应数据结构 ok
    @PostMapping("/apps/vscode/edit")
    public R<String> vsCodeEdit(@RequestBody AppReqDTO vscodeEditDTO) {
        //vsCodeEdit 返回vscode容器的url
        return R.ok(appGenerationService.vsCodeEdit(vscodeEditDTO.getAppId()));
    }

    //url  ok 请求方法 ok  请求参数  appId 修改的代码文件（不需要传，服务器中已经有了）     响应数据结构 ok
    @PostMapping("/app/complete-edit")
    public R<Boolean> completeEdit(@RequestBody AppReqDTO appReqDTO) {
        return R.ok(appGenerationService.completeEdit(appReqDTO.getAppId()));
    }

    //url  请求方法  请求参数     响应数据结构
    @PostMapping("/apps/deploy")
    public R<String> deployApp(@RequestBody AppReqDTO appReqDTO) {
        return R.ok(appGenerationService.deployApp(appReqDTO.getAppId()));
    }
}
