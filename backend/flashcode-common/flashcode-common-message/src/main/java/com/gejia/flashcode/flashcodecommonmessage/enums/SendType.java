package com.gejia.flashcode.flashcodecommonmessage.enums;

import lombok.Getter;

@Getter
public enum SendType {

    EMAIL(0, "email"),

    SMS(1, "SMS");

    SendType(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    private int value;

    private String desc;
}
