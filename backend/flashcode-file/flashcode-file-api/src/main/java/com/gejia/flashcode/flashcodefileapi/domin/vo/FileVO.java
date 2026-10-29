package com.gejia.flashcode.flashcodefileapi.domin.vo;

import lombok.Data;

@Data
public class FileVO {

    private String url;

    //路径信息   /目录/文件名.后缀名
    private String key;

    private String name;
}
