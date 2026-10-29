package com.gejia.flashcode.flashcodeportalservice.flash.controller;

import com.gejia.flashcode.flashcodeadminapi.appuser.domain.dto.AppUserListReqDTO;
import com.gejia.flashcode.flashcodeadminapi.appuser.domain.vo.AppUserVo;
import com.gejia.flashcode.flashcodecommoncore.domain.dto.BasePageDTO;
import com.gejia.flashcode.flashcodecommondomain.domain.R;
import com.gejia.flashcode.flashcodecommondomain.domain.vo.BasePageVO;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.dto.*;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.vo.AppDetailVO;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.vo.AppVO;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.vo.ChatHistoryVO;
import com.gejia.flashcode.flashcodeportalservice.flash.eums.AppListType;
import com.gejia.flashcode.flashcodeportalservice.flash.service.IAppBasesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequestMapping("/app")
public class AppController {

    @Autowired
    private IAppBasesService appBasesService;

    /**
     * 案例广场列表
     * @param appListReqDTO
     * @return
     */
    @PostMapping("/list")
    public R<BasePageVO<AppVO>> list(@RequestBody AppListReqDTO appListReqDTO) {
        appListReqDTO.setListType(AppListType.DEPOLOY.getValue());
        BasePageDTO<AppDTO> basePageDTO =  appBasesService.listApps(appListReqDTO);
        BasePageVO<AppVO> result = new BasePageVO<>();
        BeanUtils.copyProperties(basePageDTO, result);
        return R.ok(result);
    }

    /**
     * 我的应用列表
     * @param appListReqDTO
     * @return
     */
    @PostMapping("/my/list")
    public R<BasePageVO<AppVO>> myList(@RequestBody AppListReqDTO appListReqDTO) {
        appListReqDTO.setListType(AppListType.MY.getValue());
        BasePageDTO<AppDTO> basePageDTO = appBasesService.listApps(appListReqDTO);
        BasePageVO<AppVO> result = new BasePageVO<>();
        BeanUtils.copyProperties(basePageDTO, result);
        return R.ok(result);
    }

    //url  ok  请求方法 ok 请求参数 ok  响应数据结构 ok
    @GetMapping("/detail")
    public R<AppDetailVO> detail(@RequestParam("appId") Long appId) {
        return R.ok(appBasesService.detail(appId).convert2VO());
    }

    @PostMapping("/chat/history/list")
    public R<BasePageVO<ChatHistoryVO>> listChatHistory(@RequestBody ChatHistoryListReqDTO appListReqDTO) {
        BasePageDTO<ChatHistoryDTO> basePageDTO = appBasesService.listChatHistory(appListReqDTO);
        BasePageVO<ChatHistoryVO> result = new BasePageVO<>();
        BeanUtils.copyProperties(basePageDTO, result);
        return R.ok(result);
    }

}
