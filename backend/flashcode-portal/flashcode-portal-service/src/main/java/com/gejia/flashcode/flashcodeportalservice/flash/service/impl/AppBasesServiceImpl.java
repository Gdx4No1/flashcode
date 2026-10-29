package com.gejia.flashcode.flashcodeportalservice.flash.service.impl;

import com.gejia.flashcode.flashcodeadminapi.appuser.domain.dto.AppUserDTO;
import com.gejia.flashcode.flashcodecommoncore.domain.dto.BasePageDTO;
import com.gejia.flashcode.flashcodecommoncore.utils.BeanCopyUtil;
import com.gejia.flashcode.flashcodecommondomain.domain.vo.BasePageVO;
import com.gejia.flashcode.flashcodecommondomain.exception.ServiceException;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.dto.*;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.entity.App;
import com.gejia.flashcode.flashcodeportalservice.flash.eums.AppListType;
import com.gejia.flashcode.flashcodeportalservice.flash.eums.DeployStatus;
import com.gejia.flashcode.flashcodeportalservice.flash.mapper.AppMapper;
import com.gejia.flashcode.flashcodeportalservice.flash.mapper.ChatHistoryMapper;
import com.gejia.flashcode.flashcodeportalservice.flash.service.IAppBasesService;
import com.gejia.flashcode.flashcodeportalservice.user.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AppBasesServiceImpl implements IAppBasesService {

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private IUserService userService;

    @Autowired
    private ChatHistoryMapper chatHistoryMapper;

    @Override
    public BasePageDTO<AppDTO> listApps(AppListReqDTO appListReqDTO) {
        // 1  查询列表类型判断 + 转换
        int listType = appListReqDTO.getListType();
        if (AppListType.DEPOLOY.getValue() == listType) {
            //案例广场/ 已经部署的应用列表
            appListReqDTO.setDeployStatus(DeployStatus.DEPLOYED.getValue());
//            Integer totals = appMapper.selectAppsCount(appListReqDTO);//查询已经部署的应用的总数  select count(1) from app where deploy_status = 1  and appType = #{appType}
//            List<AppDTO> appDTOList = appMapper.selectPage(appListReqDTO);//  select userId , appName, appDesc, appType ,appScreenshot where deploy_status = 1  and appType = #{appType} limit offset, pageSize
        } else if (AppListType.MY.getValue() == listType) {
            //我的应用列表
            Long userId = userService.getLoginUser().getUserId();
            appListReqDTO.setUserId(userId);
//            Integer totals = appMapper.selectAppsCount(appListReqDTO);// 查询当前用户的应用总数  select count(1) from app where user_id = #{当前用户Id} and appType = #{appType}
//            List<AppDTO> appDTOList = appMapper.selectPage(appListReqDTO);//select userId , appName, appDesc, appType ,appScreenshot where user_id = #{当前用户Id} and appType = #{appType} limit offset, pageSize
        }
        // 2 查询总数
        BasePageDTO<AppDTO> result = new BasePageDTO();
        Integer totals = appMapper.selectAppsCount(appListReqDTO);
        if (totals == 0) {
            result.setTotals(0);
            result.setTotalPages(0);
            result.setList(new ArrayList<>());
            return result;
        }
        result.setTotals(totals);
        result.setTotalPages(
                BasePageDTO.calculateTotalPages(totals, appListReqDTO.getPageSize())
        );

        // 3 分页查询
        List<AppDTO> appDTOList = appMapper.selectPage(appListReqDTO);

        // 4 超页
        if (CollectionUtils.isEmpty(appDTOList)) {
            result.setList(new ArrayList<>());
            return result;
        }
        // 5 对象列表结果转换
        List<Long> userIdList = appDTOList.stream().map(AppDTO::getUserId).distinct().toList();
        Map<Long, AppUserDTO> userDTOMap =  userService.listAppUsers(userIdList);
        if (!CollectionUtils.isEmpty(userDTOMap)) {
            appDTOList.forEach(appDTO -> appDTO.setUserName(userDTOMap.get(appDTO.getUserId()).getNickName()));
        }
        result.setList(appDTOList);
        return result;
    }

    @Override
    public AppDetailDTO detail(Long appId) {
        App app = appMapper.selectById(appId);
        if (app == null) {
            throw new ServiceException("应用不存在");
        }
        checkPermission(app);
        AppDetailDTO dto = new AppDetailDTO();
        BeanCopyUtil.copyProperties(app, dto);
        return dto;
    }

    @Override
    public BasePageDTO<ChatHistoryDTO> listChatHistory(ChatHistoryListReqDTO appListReqDTO) {
        App app = appMapper.selectById(appListReqDTO.getAppId());
        if (app == null) {
            throw new ServiceException("应用不存在");
        }
        checkPermission(app);
        Integer totals = chatHistoryMapper.selectChatHistoryCount(appListReqDTO);
        BasePageDTO<ChatHistoryDTO> result = new BasePageDTO();
        if (totals == 0) {
            result.setTotals(0);
            result.setTotalPages(0);
            result.setList(new ArrayList<>());
            return result;
        }
        List<ChatHistoryDTO> historyDTOList = chatHistoryMapper.selectChatHistoryPage(appListReqDTO);
        result.setTotals(totals);
        result.setTotalPages(
                BasePageDTO.calculateTotalPages(totals, appListReqDTO.getPageSize())
        );
        if (CollectionUtils.isEmpty(historyDTOList)) {
            result.setList(new ArrayList<>());
            return result;
        }
        result.setList(historyDTOList);
        return result;
    }

    private void checkPermission(App app) {
        Long userId = userService.getLoginUser().getUserId();
        boolean hasPermission = false;
        if (userId != null && userId.equals(app.getUserId())) {
            // 当前用户自己创建的应用，无论是否发布都可以查看
            hasPermission = true;
        } else {
            // 非当前用户创建的应用，仅处于部署状态的应用可以查看
            if (app.getDeployStatus() != null && app.getDeployStatus() == DeployStatus.DEPLOYED.getValue()) {
                hasPermission = true;
            }
        }

        if (!hasPermission) {
            throw new ServiceException("无权查看该应用的详情");
        }
    }
}
