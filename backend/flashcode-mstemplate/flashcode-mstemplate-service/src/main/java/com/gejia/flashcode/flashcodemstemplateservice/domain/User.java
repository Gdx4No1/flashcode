package com.gejia.flashcode.flashcodemstemplateservice.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Getter
@Setter
@ToString
public class User implements Serializable {

    private String name;

    private int age;
}
