package com.gejia.flashcode.flashcodeportalservice.flash.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.dto.AppDTO;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.dto.AppListReqDTO;
import com.gejia.flashcode.flashcodeportalservice.flash.domain.entity.App;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppMapper extends BaseMapper<App> {
    Integer selectAppsCount(AppListReqDTO appListReqDTO);

    List<AppDTO> selectPage(AppListReqDTO appListReqDTO);
}
