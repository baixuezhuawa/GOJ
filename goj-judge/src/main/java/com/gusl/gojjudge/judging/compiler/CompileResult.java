package com.gusl.gojjudge.judging.compiler;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

@Data
public class CompileResult {

    private String status;

    private String compileMsg;

    private JSONObject activeFile;

}
