package com.gusl.gojjudge.sercice.compiler;

import com.alibaba.fastjson2.JSONObject;
import com.gusl.common.constant.SandBoxStatus;
import com.gusl.gojjudge.adapter.AbstractLanguageAdapter;
import com.gusl.gojjudge.exception.SystemErrorException;
import com.gusl.gojjudge.pojo.entity.CompileResult;
import com.gusl.gojjudge.pojo.entity.SandBoxResult;
import com.gusl.gojjudge.sercice.runner.GoJudgeExecuter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * 编译工具
 * <p>只负责将代码交给沙箱编译是否顺利</p>
 * <p>不负责判断代码交给沙箱编译是否成功</p>
 */
@Component
@RequiredArgsConstructor
public class Compiler {

    private final GoJudgeExecuter goJudgeExecuter;

    /**
     * 编译
     * @param sourceCode 源代码
     * @param adapter 语言适配器
     * @return 编译结果
     */
    public CompileResult compile(String sourceCode, AbstractLanguageAdapter adapter){
        CompileResult compileResult = new CompileResult();

        // 无需编译直接封装结果就行.
        if(!adapter.isNeedCompile()){
            compileResult.setStatus(SandBoxStatus.ACCEPTED);
            compileResult.setCompileMsg("无需编译");
            compileResult.setActiveFile(JSONObject.of("content", sourceCode));
        }

        JSONObject compileRequest = adapter.createCompileRequest(sourceCode);

        try {
            SandBoxResult sandBoxResult = goJudgeExecuter.execute(compileRequest);

            compileResult.setStatus(sandBoxResult.getStatus());

            compileResult.setActiveFile(JSONObject.of("fileId", sandBoxResult.getFileIds().get("fileId")));

            compileResult.setCompileMsg(String.format(
                    "stdout: %s\nstderr: %s",
                    sandBoxResult.getFiles().get("stdout"),
                    sandBoxResult.getFiles().get("stderr")
            ));

        } catch (Exception se) {
            throw new SystemErrorException("编译失败, 系统异常: \n" + se.getMessage());

        }

        return compileResult;
    }


    /**
     * 删除编译后的缓存文件
     * @param result 编译结果
     */
    public void deleteCacheCompileFile(CompileResult result){
        if (result.getActiveFile().containsKey("fileId")){
            goJudgeExecuter.deleteCacheFile((String) result.getActiveFile().get("fileId"));
        }
    }

}
