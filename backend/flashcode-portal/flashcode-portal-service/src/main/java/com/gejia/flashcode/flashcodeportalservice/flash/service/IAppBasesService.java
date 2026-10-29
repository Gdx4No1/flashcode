package com.gejia.flashcode.flashcodeportalservice.flash.service;

import com.gejia.flashcode.flashcodecommoncore.domain.dto.BasePageDTO;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.dto.*;

public interface IAppBasesService {
    BasePageDTO<AppDTO> listApps(AppListReqDTO appListReqDTO);

    AppDetailDTO detail(Long appId);

    BasePageDTO<ChatHistoryDTO> listChatHistory(ChatHistoryListReqDTO appListReqDTO);
}
