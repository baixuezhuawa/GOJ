package com.gusl.gojjudge.sandbox;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 沙箱运行请求表单
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SandboxRunRequestForm {

    /** 沙箱内执行命令的参数 */
    private List<String> args;

    /** 沙箱环境变量, 通畅为PATH */
    private List<String> env;

    /** cpu限制 */
    private Long cpuLimit;

    /** cpu钟限制 */
    private Long realCpuLimit;

    /** 空间限制 */
    private Long memoryLimit;

    /** 栈限制 */
    private Long stackLimit;

    /** 核心数限制 */
    private Long procLimit;

    /** 输入输出文件 */
    private List<JSONObject> files;

    /** 执行文件 */
    private Map<String, JSONObject> copyIn;

    /** 输出文件 */
    private List<String> copyOut;

    /** 编译缓存名 */
    private List<String> copyCached;


    /**
     * 生成沙箱运行请求
     * @param forms 请求表单
     * @return 请求
     */
    public JSONObject generate(SandboxRunRequestForm...forms){

        List<JSONObject> cmdList = new ArrayList<>();

        for(SandboxRunRequestForm form : forms){
            cmdList.add(generateOne(form));
        }

        return JSONObject.of("cmd", cmdList);
    }

    public JSONObject generate(){
        return JSONObject.of("cmd", List.of(generateOne(this)));
    }

    /**
     * 生成沙箱运行请求
     */
    private JSONObject generateOne(SandboxRunRequestForm form){

        JSONObject cmd = new JSONObject();

        if(!CollectionUtil.isEmpty(form.getEnv())){
            cmd.put("env", form.getEnv());
        }

        cmd.put("args", form.getArgs());

        cmd.put("cpuLimit", form.getCpuLimit());

        cmd.put("realCpuLimit", form.getRealCpuLimit());

        cmd.put("memoryLimit", form.getMemoryLimit());

        cmd.put("stackLimit", form.getMemoryLimit());

        cmd.put("files", form.getFiles());

        JSONObject copyIn = new JSONObject();
        copyIn.putAll(form.getCopyIn());
        cmd.put("copyIn", copyIn);

        cmd.put("copyOut", form.getCopyOut());

        if(!CollectionUtil.isEmpty(form.getCopyCached())){
            cmd.put("copyOut", form.getCopyOut());
        }

        return cmd;
    }

}
