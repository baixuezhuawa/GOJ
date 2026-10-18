package com.gusl.gojjudge.judging.language;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.gusl.gojjudge.judging.language.properties.CompileLimitInfo;
import com.gusl.gojjudge.judging.language.properties.LanguageConfig;
import com.gusl.gojjudge.judging.language.properties.RunLimitInfo;
import com.gusl.gojjudge.judging.runner.RunContext;
import com.gusl.gojjudge.judging.sandbox.SandboxRunRequestForm;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;

/**
 * 配置驱动的语言适配器，使用独立配置快照组装沙箱请求。
 */
public class ConfigurableLanguageAdapter extends AbstractLanguageAdapter {

    /** 仅供当前适配器读取，不向外暴露或修改。 */
    private final LanguageConfig config;

    /** 深拷贝并校验配置，使后续配置变更不影响已经取得的适配器。 */
    public ConfigurableLanguageAdapter(LanguageConfig config){
        Assert.notNull(config, "语言配置不能为空");
        // Fastjson2 深拷贝嵌套的限制对象和参数集合，避免共享可变配置。
        this.config = JSON.parseObject(JSON.toJSONString(config), LanguageConfig.class);
        this.config.validate();
    }

    /** 返回提交使用的语言编码。 */
    @Override
    public String languageCode(){
        return config.getCode();
    }

    /** 返回运行时挂载的文件名，脚本语言直接使用源码文件名。 */
    @Override
    public String activeFileName(){
        return isNeedCompile() ? config.getActiveFileName() : config.getSourceFileName();
    }

    /** 判断当前语言是否需要编译。 */
    @Override
    public boolean isNeedCompile(){
        return config.getNeedCompile();
    }

    /** 将源码写入配置指定的文件，并缓存编译产物。 */
    @Override
    public JSONObject createCompileRequest(String sourceCode){
        if(!isNeedCompile()){
            return null;
        }

        // 使用配置中的完整参数数组，不拼接用户源码到命令中。
        SandboxRunRequestForm form = new SandboxRunRequestForm();
        form.setArgs(buildCompileArgs());
        form.setEnv(List.copyOf(config.getEnv()));

        // 时间由毫秒换算为纳秒，内存和栈由 KB 换算为字节。
        CompileLimitInfo compile = config.getCompile();
        form.setCpuLimit(compile.getCpuLimitMs() * 1000_000L);
        form.setRealCpuLimit(compile.getRealCpuLimitMs() * 1000_000L);
        form.setMemoryLimit(compile.getMemoryLimitKb() * 1024L);
        form.setStackLimit(compile.getStackLimitKb() * 1024L);
        form.setProcLimit(compile.getProcLimit().longValue());

        // 编译阶段标准输入为空，源码与标准输出分别挂载。
        form.setFiles(buildFiles("", compile.getStdoutLimitBytes(), compile.getStderrLimitBytes()));
        form.setCopyIn(Map.of(config.getSourceFileName(), JSONObject.of("content", sourceCode)));
        form.setCopyOut(List.of("stdout", "stderr"));
        form.setCopyCached(List.of(activeFileName()));
        return form.generate();
    }

    /** 挂载编译产物或脚本源码，并将测试输入传入标准输入。 */
    @Override
    public JSONObject createRunRequest(RunContext context){
        SandboxRunRequestForm form = new SandboxRunRequestForm();
        form.setArgs(buildRunArgs());
        form.setEnv(List.copyOf(config.getEnv()));

        // 题目决定时间和内存上限，语言配置补充墙钟倍数、栈和进程限制。
        RunLimitInfo run = config.getRun();
        form.setCpuLimit(context.getTimeLimitMs() * 1000_000L);
        form.setRealCpuLimit(context.getTimeLimitMs() * run.getClockLimitMultiplier() * 1000_000L);
        form.setMemoryLimit(context.getMemoryLimitKb() * 1024L);
        form.setStackLimit(run.getStackLimitKb() * 1024L);
        form.setProcLimit(run.getProcLimit().longValue());

        // activeFile 由 Compiler 提供，分别为 fileId 编译产物或 content 脚本源码。
        form.setFiles(buildFiles(context.getInput(), run.getStdoutLimitBytes(), run.getStderrLimitBytes()));
        form.setCopyIn(Map.of(activeFileName(), context.getActiveFile()));
        form.setCopyOut(List.of("stdout", "stderr"));
        return form.generate();
    }

    /** 返回运行参数的只读副本。 */
    @Override
    protected List<String> buildRunArgs(){
        return List.copyOf(config.getRunArgs());
    }

    /** 返回编译参数的只读副本。 */
    @Override
    protected List<String> buildCompileArgs(){
        return List.copyOf(config.getCompileArgs());
    }

    /** 按 go-judge 要求依次组装标准输入、标准输出和标准错误。 */
    private List<JSONObject> buildFiles(String input, Integer stdoutLimit, Integer stderrLimit){
        return List.of(
                JSONObject.of("content", input),
                JSONObject.of("name", "stdout", "max", stdoutLimit),
                JSONObject.of("name", "stderr", "max", stderrLimit)
        );
    }
}
