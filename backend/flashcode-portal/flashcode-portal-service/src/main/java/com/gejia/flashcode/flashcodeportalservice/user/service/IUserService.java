package com.gejia.flashcode.flashcodeportalservice.user.service;

import com.gejia.flashcode.flashcodeadminapi.appuser.domain.dto.AppUserDTO;
import com.gejia.flashcode.flashcodeadminapi.appuser.domain.dto.UserEditReqDTO;
import com.gejia.flashcode.flashcodecommonsecurity.domain.dto.TokenDTO;
import com.gejia.flashcode.flashcodeportalservice.user.entity.dto.EmailCodeLoginDTO;
import com.gejia.flashcode.flashcodeportalservice.user.entity.dto.LoginDTO;
import com.gejia.flashcode.flashcodeportalservice.user.entity.dto.UserDTO;

import java.util.List;
import java.util.Map;

/**
 * 门户用户服务接口
 */
public interface IUserService {

    TokenDTO login(LoginDTO loginDTO);

    String sendCode(String phone);

    String emailSendCode(String email);

    void edit(UserEditReqDTO userEditReqDTO);

    UserDTO getLoginUser();

    void logout();

    Map<Long, AppUserDTO> listAppUsers(List<Long> userIdList);
}
