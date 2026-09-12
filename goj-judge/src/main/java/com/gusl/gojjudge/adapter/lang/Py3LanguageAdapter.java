package com.gusl.gojjudge.adapter.lang;

import com.alibaba.fastjson2.JSONObject;
import com.gusl.gojjudge.adapter.AbstractLanguageAdapter;
import com.gusl.gojjudge.pojo.entity.CompilePlan;
import com.gusl.gojjudge.pojo.entity.RunContext;
import com.gusl.gojjudge.pojo.entity.RunLimitInfo;
import com.gusl.gojjudge.properties.lang.Py3Properties;
import com.gusl.gojjudge.sandbox.SandboxRunRequestForm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Python3 语言适配器
 */
@Component
@RequiredArgsConstructor
public class Py3LanguageAdapter extends AbstractLanguageAdapter {

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
    public boolean isNeedCompile() {
        return false;
    }


    @Override
    public JSONObject createCompileRequest(String sourceCode) {

        return null;
    }

    @Override
    public JSONObject createRunRequest(RunContext context) {
        SandboxRunRequestForm form = new SandboxRunRequestForm();

        form.setArgs(buildRunArgs());

        // cpu limit
        form.setCpuLimit(context.getTimeLimitMs() * 1000_000L);

        // real cpu limit
        form.setRealCpuLimit(context.getTimeLimitMs() * 3L * 1000_000L);

        // memory limit
        form.setMemoryLimit(context.getMemoryLimitKb() * 1024L * 1024L);


        RunLimitInfo run = py3.getRun();

        // stack limit
        form.setStackLimit(run.getStackLimitKb() * 1024L * 1024L);

        // proc limit
        form.setProcLimit(run.getProcLimit().longValue());

        // files
        JSONObject content = JSONObject.of("content", "");
        JSONObject stdout = JSONObject.of(
                "name", "stdout",
                "max", run.getStdoutLimitBytes()
        );
        JSONObject stderr = JSONObject.of(
                "name", "stderr",
                "max", run.getStderrLimitBytes()
        );
        form.setFiles(List.of(content, stdout, stderr));

        // copyIn
        form.setCopyIn(Map.of("Main.py", JSONObject.of("content", context.getInput())));

        form.setCopyOut(List.of("stdout", "stderr"));

        return form.generate();
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
