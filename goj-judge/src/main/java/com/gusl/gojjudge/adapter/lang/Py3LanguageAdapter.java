package com.gusl.gojjudge.adapter.lang;

import com.alibaba.fastjson2.JSONObject;
import com.gusl.gojjudge.adapter.AbstractLanguageAdapter;
import com.gusl.gojjudge.pojo.entity.CompilePlan;
import com.gusl.gojjudge.pojo.entity.RunContext;
import com.gusl.gojjudge.properties.lang.Py3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Python3 语言适配器
 */
@Component
@RequiredArgsConstructor
public class Py3LanguageAdapter extends AbstractLanguageAdapter {

    /** Python3 程序运行时标准输出上限，单位为字节。 */
    private static final int DEFAULT_RUN_STDOUT_LIMIT_BYTES = 1024 * 1024;

    /** Python3 程序运行时标准错误输出上限，单位为字节。 */
    private static final int DEFAULT_RUN_STDERR_LIMIT_BYTES = 16 * 1024 * 1024;

    /** 由 Spring 绑定的 python3 工具链和编译配置。 */
    private final Py3Properties py3;


    @Override
    public String languageCode() {
        return py3.getCode();
    }

    @Override
    public String activeFileName() {
        return py3.getActiveFileName();
    }

    @Override
    public CompilePlan createCompilePlan(String sourceCode) {
        return CompilePlan.builder()
                .required(false)
                .build();
    }

    @Override
    public JSONObject createRunRequest(RunContext runContext) {
        JSONObject stdin = JSONObject.of("content", runContext.getInput());
        JSONObject stdout = JSONObject.of(
                "name", "stdout",
                "max", DEFAULT_RUN_STDOUT_LIMIT_BYTES
        );
        JSONObject stderr = JSONObject.of(
                "name", "stderr",
                "max", DEFAULT_RUN_STDERR_LIMIT_BYTES
        );


        JSONObject copyFileName = createProgramCopyIn(runContext.getProgram());

        // 运行请求只引用已缓存的 jar，不再重复编译，也不创建新的缓存文件。
        return buildCommonRequest(
                buildRunArgs(),
                py3.getEnv(),
                stdin,
                stdout,
                stderr,
                copyFileName,
                null,
                runContext.getLimit(),
                null
        );
    }

    @Override
    protected JSONObject createCompileRequest(String sourceCode) {
        return null;
    }

    @Override
    protected List<String> buildRunArgs() {
        return List.of("/usr/bin/python3", "/w/" + py3.getActiveFileName());
    }

    @Override
    protected List<String> buildCompileArgs() {
        return null;
    }
}
