package com.gejia.flashcode.flashcodeportalservice.flash.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gejia.flashcode.flashcodecommoncore.domain.entity.BaseDO;
import lombok.Data;

@Data
@TableName("chat_history")
public class ChatHistory extends BaseDO {

    /**
     * 应用id
     */
    @TableField("app_id")
    private Long appId;

    /**
     * 消息类型 0：用户，1：大模型
     */
    @TableField("msg_role")
    private int msgRole;

    /**
     * 消息内容
     */
    @TableField("content")
    private String content;
}
