package com.gejia.flashcode.flashcodeportalservice.user.entity.vo;

import com.gejia.flashcode.flashcodecommondomain.domain.vo.LoginUserVO;
import lombok.Data;

/**
 * C端用户VO
 */
@Data
public class UserVo extends LoginUserVO {

    /**
     * 用户头像
     */
    private String avatar;

    /**
     * 昵称
     */
    private String nickName;
}
