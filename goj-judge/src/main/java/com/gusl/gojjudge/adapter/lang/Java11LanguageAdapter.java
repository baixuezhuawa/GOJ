package com.gusl.gojjudge.adapter.lang;

import com.alibaba.fastjson2.JSONObject;
import com.gusl.gojjudge.adapter.AbstractLanguageAdapter;
import com.gusl.gojjudge.pojo.entity.CompileLimitInfo;
import com.gusl.gojjudge.pojo.entity.RunContext;
import com.gusl.gojjudge.pojo.entity.RunLimitInfo;
import com.gusl.gojjudge.properties.lang.Java11Properties;
import com.gusl.gojjudge.sandbox.SandboxRunRequestForm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Java 11 语言适配器。
 */
@Component
@RequiredArgsConstructor
public class Java11LanguageAdapter extends AbstractLanguageAdapter {

    /** 由 Spring 绑定的 Java 11 工具链和编译配置。 */
    private final Java11Properties java11;

    /** 返回 server 与 Judge 之间约定的语言编码。 */
    @Override
    public String languageCode() {
        return java11.getCode();
    }

    @Override
    public String activeFileName(){
        return java11.getActiveCacheFileName();
    }

    @Override
    public boolean isNeedCompile() {
        return true;
    }

    /**
     * 构造 Java 编译请求。
     *
     * @param sourceCode 用户提交的 Java 源码
     * @return go-judge 编译请求体
     */
    @Override
    public JSONObject createCompileRequest(String sourceCode) {
        SandboxRunRequestForm form = new SandboxRunRequestForm();

        // args
        form.setArgs(buildCompileArgs());

        // env
        form.setEnv(List.of(java11.getEnv()));


        CompileLimitInfo compileInfo = java11.getCompile();

        // cpu limit
        form.setCpuLimit(compileInfo.getCpuLimitMs() * 1000_000L);

        // real cpu limit
        form.setRealCpuLimit(compileInfo.getRealCpuLimitMs() * 1000_000L);

        // memory limit
        form.setMemoryLimit(compileInfo.getMemoryLimitKb() * 1024L * 1024L);

        // stack limit
        form.setStackLimit(compileInfo.getStackLimitKb() * 1024L * 1024L);

        // proc limit
        form.setProcLimit(compileInfo.getProcLimit().longValue());

        // files
        JSONObject content = JSONObject.of("content", "");
        JSONObject stdout = JSONObject.of(
                "name", "stdout",
                "max", compileInfo.getStdoutLimitBytes()
        );
        JSONObject stderr = JSONObject.of(
                "name", "stderr",
                "max", compileInfo.getStderrLimitBytes()
        );
        form.setFiles(List.of(content, stdout, stderr));

        // copyIn
        form.setCopyIn(Map.of("Main.java", JSONObject.of("content", sourceCode)));

        form.setCopyOut(List.of("stdout", "stderr"));

        form.setCopyCached(List.of("Main.java"));

        return form.generate();
    }

    /**
     * 构造 Java 运行请求。
     *
     * @param context 当前测试点的输入、运行限制和编译产物信息
     * @return go-judge 运行请求体
     */
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


        RunLimitInfo run = java11.getRun();

        // stack limit
        form.setStackLimit(run.getStackLimitKb() * 1024L * 1024L);

        // proc limit
        form.setProcLimit(run.getProcLimit().longValue());

        // files
        JSONObject content = JSONObject.of("content", context.getInput());
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
        form.setCopyIn(Map.of("Main.java", context.getActiveFile()));

        form.setCopyOut(List.of("stdout", "stderr"));

        return form.generate();
    }

    /**
     * 返回 Java 运行命令。
     *
     * @return 使用 UTF-8 编码并执行 Main 类的命令参数
     */
    @Override
    protected List<String> buildRunArgs() {
        return List.of(
                java11.getJava(),
                "-Dfile.encoding=UTF-8",
                "-cp",
                "/w/Main.jar",
                "Main"
        );
    }

    /**
     * 返回 Java 编译命令。
     *
     * <p>使用 bash 串联 javac 与 jar；任一步失败都会使 go-judge 返回非零退出状态，
     * JudgeService 随后把结果归类为编译错误。</p>
     *
     * @return go-judge 编译命令参数
     */
    @Override
    protected List<String> buildCompileArgs() {
        String command = String.format(
                "%s -encoding UTF-8 Main.java && %s -cf %s *.class",
                java11.getJavac(),
                java11.getJar(),
                java11.getActiveCacheFileName()
        );
        return List.of("/bin/bash", "-c", command);
    }
}
