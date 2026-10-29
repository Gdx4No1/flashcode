package com.gejia.flashcode.flashcodeadminservice.map.controller;

import com.gejia.flashcode.flashcodeadminapi.map.domain.dto.LocationReqDTO;
import com.gejia.flashcode.flashcodeadminapi.map.domain.dto.PlaceSearchReqDTO;
import com.gejia.flashcode.flashcodeadminapi.map.domain.vo.RegionCityVo;
import com.gejia.flashcode.flashcodeadminapi.map.domain.vo.RegionVO;
import com.gejia.flashcode.flashcodeadminapi.map.domain.vo.SearchPoiVo;
import com.gejia.flashcode.flashcodeadminapi.map.feign.MapFeignClient;
import com.gejia.flashcode.flashcodeadminservice.map.domain.dto.RegionCityDTO;
import com.gejia.flashcode.flashcodeadminservice.map.domain.dto.SearchPoiDTO;
import com.gejia.flashcode.flashcodeadminservice.map.domain.dto.SysRegionDTO;
import com.gejia.flashcode.flashcodeadminservice.map.service.IFlashcodeMapService;
import com.gejia.flashcode.flashcodecommoncore.domain.dto.BasePageDTO;
import com.gejia.flashcode.flashcodecommoncore.utils.BeanCopyUtil;
import com.gejia.flashcode.flashcodecommondomain.domain.R;
import com.gejia.flashcode.flashcodecommondomain.domain.dto.BasePageReqDTO;
import com.gejia.flashcode.flashcodecommondomain.domain.vo.BasePageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 地图相关控制器类
 */
@RestController
@Slf4j
public class FlashcodeMapController implements MapFeignClient {

    @Autowired
    private IFlashcodeMapService mapService;

    /**
     * 城市列表查询
     * @return 城市列表信息
     */
    @Override
    public R<List<RegionVO>> getCityList() {
        List<SysRegionDTO> regionDTOS =  mapService.getCityList();
        List<RegionVO> result = BeanCopyUtil.copyListProperties(regionDTOS, RegionVO::new);
        return R.ok(result);
    }

    /**
     * 城市拼音归类查询
     * @return 城市字母与城市列表的哈希
     */
    @Override
    public R<Map<String, List<RegionVO>>> getCityPylist() {
        Map<String, List<RegionVO>> result = new LinkedHashMap<>();
        Map<String, List<SysRegionDTO>> pinyinList = mapService.getCityPylist();
        for (Map.Entry<String, List<SysRegionDTO>> region : pinyinList.entrySet()) {
            result.put(region.getKey(), BeanCopyUtil.copyListProperties(region.getValue(), RegionVO::new));
        }
        return R.ok(result);
    }

    /**
     * 根据父级区域ID获取子集区域列表
     * @param parentId 父级区域ID
     * @return 子集区域列表
     */
    @Override
    public R<List<RegionVO>> regionChildren(Long parentId) {
        List<SysRegionDTO> regionDTOS = mapService.getRegionChildren(parentId);
        List<RegionVO> result = BeanCopyUtil.copyListProperties(regionDTOS, RegionVO::new);
        return R.ok(result);
    }

    /**
     * 获取热门城市列表
     * @return 城市列表
     */
    @Override
    public R<List<RegionVO>> getHotCityList() {
        List<SysRegionDTO> hotCityList =  mapService.getHotCityList();
        List<RegionVO> result = BeanCopyUtil.copyListProperties(hotCityList, RegionVO::new);
        return R.ok(result);
    }

    /**
     * 根据地点搜索
     * @param placeSearchReqDTO 搜索条件
     * @return 搜索结果
     */
    @Override
    public R<BasePageVO<SearchPoiVo>> searchSuggestOnMap(PlaceSearchReqDTO placeSearchReqDTO) {
        BasePageDTO<SearchPoiDTO> basePageReqDTO =  mapService.searchSuggestOnMap(placeSearchReqDTO);
        BasePageVO<SearchPoiVo> result = new BasePageVO<>();
        BeanUtils.copyProperties(basePageReqDTO, result);
        return R.ok(result);
    }

    /**
     * 根据经纬度来定位城市
     * @param locationReqDTO 经纬度信息
     * @return 城市信息
     */
    @Override
    public R<RegionCityVo> locateCityByLocation(LocationReqDTO locationReqDTO) {
        RegionCityDTO regionCityDTO = mapService.getCityByLocation(locationReqDTO);
        RegionCityVo result = new RegionCityVo();
        BeanUtils.copyProperties(regionCityDTO, result);
        return R.ok(result);
    }
}
