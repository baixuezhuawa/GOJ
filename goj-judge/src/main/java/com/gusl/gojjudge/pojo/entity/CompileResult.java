package com.gusl.gojjudge.pojo.entity;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

@Data
public class CompileResult {

    private String status;

    private String compileMsg;

    private JSONObject activeFile;

}
