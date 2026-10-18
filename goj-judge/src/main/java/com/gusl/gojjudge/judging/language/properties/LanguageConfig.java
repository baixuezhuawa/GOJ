package com.gusl.gojjudge.judging.language.properties;

import lombok.Data;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用语言配置，描述源码、编译产物、命令参数和资源限制。
 */
@Data
public class LanguageConfig {

    /** 与 server 语言目录和提交记录一致的语言编码。 */
    private String code;

    /** 写入沙箱的源码文件名。 */
    private String sourceFileName;

    /** 是否先编译再运行，脚本语言设置为 false。 */
    private Boolean needCompile;

    /** 编译后需要缓存的文件名；脚本语言不配置。 */
    private String activeFileName;

    /** 编译和运行共用的沙箱环境变量。 */
    private List<String> env = new ArrayList<>();

    /** 完整编译参数数组，第一项是沙箱内的可执行文件。 */
    private List<String> compileArgs = new ArrayList<>();

    /** 完整运行参数数组，第一项是沙箱内的可执行文件。 */
    private List<String> runArgs = new ArrayList<>();

    /** 编译阶段限制，脚本语言不配置。 */
    private CompileLimitInfo compile;

    /** 运行阶段限制，时间和内存上限由题目提供。 */
    private RunLimitInfo run;

    /** 校验创建适配器所需的配置，错误信息包含语言编码和字段名。 */
    public void validate(){
        Assert.hasText(code, "语言编码不能为空");
        String prefix = "语言 " + code + " 配置错误: ";

        // 校验所有语言共用的文件、命令和运行限制。
        Assert.hasText(sourceFileName, prefix + "source-file-name 不能为空");
        Assert.notNull(needCompile, prefix + "need-compile 必须明确配置");
        validateArgs(runArgs, prefix + "run-args");
        Assert.notNull(env, prefix + "env 不能为 null");
        for(String value : env){
            Assert.hasText(value, prefix + "env 不能包含空值");
        }
        Assert.notNull(run, prefix + "run 不能为空");
        positive(run.getClockLimitMultiplier(), prefix + "run.clock-limit-multiplier");
        positive(run.getStackLimitKb(), prefix + "run.stack-limit-kb");
        positive(run.getProcLimit(), prefix + "run.proc-limit");
        positive(run.getStdoutLimitBytes(), prefix + "run.stdout-limit-bytes");
        positive(run.getStderrLimitBytes(), prefix + "run.stderr-limit-bytes");

        // 仅编译型语言要求编译命令、产物和编译限制。
        if(needCompile){
            Assert.hasText(activeFileName, prefix + "active-file-name 不能为空");
            validateArgs(compileArgs, prefix + "compile-args");
            Assert.notNull(compile, prefix + "compile 不能为空");
            positive(compile.getCpuLimitMs(), prefix + "compile.cpu-limit-ms");
            positive(compile.getRealCpuLimitMs(), prefix + "compile.real-cpu-limit-ms");
            positive(compile.getMemoryLimitKb(), prefix + "compile.memory-limit-kb");
            positive(compile.getStackLimitKb(), prefix + "compile.stack-limit-kb");
            positive(compile.getProcLimit(), prefix + "compile.proc-limit");
            positive(compile.getStdoutLimitBytes(), prefix + "compile.stdout-limit-bytes");
            positive(compile.getStderrLimitBytes(), prefix + "compile.stderr-limit-bytes");
        }
    }

    /** 命令必须包含可执行文件，其余参数允许空字符串但不能为 null。 */
    private void validateArgs(List<String> args, String field){
        Assert.notEmpty(args, field + " 不能为空");
        Assert.hasText(args.get(0), field + " 第一项必须是可执行文件");
        Assert.noNullElements(args, field + " 不能包含 null");
    }

    /** 校验资源限制为正数。 */
    private void positive(Integer value, String field){
        Assert.isTrue(value != null && value > 0, field + " 必须为正数");
    }
}
