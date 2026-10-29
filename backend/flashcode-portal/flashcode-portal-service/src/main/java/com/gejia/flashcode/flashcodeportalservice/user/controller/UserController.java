package com.gejia.flashcode.flashcodeportalservice.user.controller;

import com.gejia.flashcode.flashcodeadminapi.appuser.domain.dto.UserEditReqDTO;
import com.gejia.flashcode.flashcodecommondomain.domain.R;
import com.gejia.flashcode.flashcodecommondomain.domain.vo.TokenVO;
import com.gejia.flashcode.flashcodeportalservice.user.entity.dto.CodeLoginDTO;
import com.gejia.flashcode.flashcodeportalservice.user.entity.dto.EmailCodeLoginDTO;
import com.gejia.flashcode.flashcodeportalservice.user.entity.dto.WechatLoginDTO;
import com.gejia.flashcode.flashcodeportalservice.user.entity.vo.UserVo;
import com.gejia.flashcode.flashcodeportalservice.user.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 门户程序用户入口
 */
@RequestMapping("/user")
@RestController
@Slf4j
public class UserController {

    @Autowired
    private IUserService userService;

    /**
     * 微信登录
     * @param wechatLoginDTO 微信登录DTO
     * @return token令牌
     */
    @PostMapping("/login/wechat")
    public R<TokenVO> login(@RequestBody @Validated WechatLoginDTO wechatLoginDTO) {
        return R.ok(userService.login(wechatLoginDTO).convertToVo());
    }

    /**
     * 发送短信验证码
     * @param phone 手机号
     * @return 验证码
     */
    @GetMapping("/send_code")
    public R<String> sendCode(String phone) {
        return R.ok(userService.sendCode(phone));
    }

    /**
     * 发送邮箱验证码
     * @param email 邮箱账号
     * @return  验证码
     */
    @GetMapping("/email/send_code")
    public R<String> emailSendCode(String email) {
        return R.ok(userService.emailSendCode(email));
    }

    /**
     * 验证码登录
     * @param codeLoginDTO 验证码登录信息
     * @return token信息VO
     */
    @PostMapping("/login/code")
    public R<TokenVO> login(@RequestBody @Validated CodeLoginDTO codeLoginDTO) {
        return R.ok(userService.login(codeLoginDTO).convertToVo());
    }

    /**
     * 邮箱验证码登录
     * @param codeLoginDTO 验证码登录信息
     * @return token信息VO
     */
    @PostMapping("/login/email/code")
    public R<TokenVO> emailLogin(@RequestBody @Validated EmailCodeLoginDTO codeLoginDTO) {
        return R.ok(userService.login(codeLoginDTO).convertToVo());
    }

    /**
     * 修改用户信息
     * @param userEditReqDTO C端用户编辑DTO
     * @return void
     */
    @PostMapping("/edit")
    public R<Void> edit(@RequestBody @Validated UserEditReqDTO userEditReqDTO) {
        userService.edit(userEditReqDTO);
        return R.ok();
    }

    /**
     * 获取用户登录信息
     * @return 用户信息VO
     */
    @GetMapping("/login_info/get")
    public R<UserVo> getLoginUser() {
        return R.ok(userService.getLoginUser().convertToVO());
    }

    /**
     * 退出登录
     * @return void
     */
    @DeleteMapping("/logout")
    R<Void> logout() {
        userService.logout();
        return R.ok();
    }
}
