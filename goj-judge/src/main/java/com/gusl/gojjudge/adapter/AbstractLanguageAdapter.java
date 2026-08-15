package com.gusl.gojjudge.adapter;

import com.alibaba.fastjson2.JSONObject;
import com.gusl.gojjudge.pojo.entity.*;

import java.util.List;

/**
 * go-judge 语言适配器的公共基类。
 *
 * <p>子类只实现语言特有的命令参数和文件布局，本类统一组装 go-judge 的
 * {@code cmd} 请求、设置资源限制、挂载输入输出文件以及转换资源单位。
 * 适配器不负责发起 HTTP 请求、不更新提交状态，也不执行用户代码。</p>
 */
public abstract class AbstractLanguageAdapter {

    /** go-judge 墙钟时间通常需要比 CPU 时间更宽松，避免 IO 等待被误判。 */
    private static final long REAL_CPU_LIMIT_MULTIPLIER = 3L;

    /** 没有单独配置运行栈大小时使用的默认值，单位为字节。 */
    private static final long DEFAULT_STACK_LIMIT_BYTES = 128L * 1024 * 1024;

    /** 限制用户程序及其子进程总数的默认值。 */
    private static final int DEFAULT_PROC_LIMIT = 64;

    /**
     * 返回提交使用的语言编码。
     *
     * @return 与 server 语言配置和提交记录一致的语言编码
     */
    public abstract String languageCode();

    /**
     * 沙箱编译后可执行文件名
     */
    public abstract String activeFileName();

    /**
     * 创建编译计划。编译计划包含是否需要编译、发送给沙箱的请求体和编译产物名称。
     *
     * @param sourceCode 用户提交的源代码
     * @return 编译阶段使用的计划
     */
    public abstract CompilePlan createCompilePlan(String sourceCode);

    /**
     * 根据一次测试点运行上下文创建 go-judge 请求体。
     *
     * @param runContext 测试输入、编译产物和运行时资源限制
     * @return 可直接发送给 go-judge {@code /run} 接口的 JSON 请求
     */
    public abstract JSONObject createRunRequest(RunContext runContext);

    /**
     * 创建当前语言的编译请求。
     *
     * @param sourceCode 用户提交的源代码
     * @return 包含编译命令和源文件的 go-judge 请求体
     */
    protected abstract JSONObject createCompileRequest(String sourceCode);

    /**
     * 返回运行用户程序时使用的命令参数。
     *
     * @return go-judge {@code cmd.args} 参数
     */
    protected abstract List<String> buildRunArgs();

    /**
     * 返回编译用户程序时使用的命令参数。
     *
     * @return go-judge {@code cmd.args} 参数
     */
    protected abstract List<String> buildCompileArgs();

    /**
     * 组装编译或运行请求中的公共部分。
     *
     * <p>{@code compileLimit} 与 {@code runtimeLimit} 必须且只能有一个不为空，
     * 用它区分当前请求是编译阶段还是运行阶段。GOJ 业务配置使用毫秒和 KB，
     * go-judge 协议使用纳秒和字节，因此资源字段会在这里统一转换。</p>
     *
     * @param args 沙箱内执行的命令参数
     * @param env 沙箱环境变量，通常为 PATH
     * @param stdin 标准输入文件配置
     * @param stdout 标准输出文件配置
     * @param stderr 标准错误输出文件配置
     * @param copyFileName 需要复制到沙箱工作目录的文件
     * @param compileLimit 编译阶段限制，单位为毫秒、KB
     * @param runtimeLimit 运行阶段限制，单位为毫秒、KB
     * @param copyOutCached 需要缓存并返回文件 ID 的产物名称；运行阶段传 null
     * @return go-judge {@code /run} 请求体
     * @throws IllegalArgumentException 两类限制同时为空或同时存在，或限制值非法时抛出
     */
    protected JSONObject buildCommonRequest(
            List<String> args,
            String env,
            JSONObject stdin,
            JSONObject stdout,
            JSONObject stderr,
            JSONObject copyFileName,
            CompileLimitInfo compileLimit,
            RuntimeLimit runtimeLimit,
            String copyOutCached
    ) {
        JSONObject cmd = new JSONObject();
        cmd.put("args", args);

        if (env != null && !env.isBlank()) {
            cmd.put("env", List.of(env));
        }

        // 请求必须明确属于编译或运行阶段，避免把错误的资源限制发送给沙箱。
        if ((compileLimit == null) == (runtimeLimit == null)) {
            throw new IllegalArgumentException("只需要一种限制类型");
        }

        /*
        go-judge 资源字段说明（单位均为协议单位）:
            cpuLimit: 进程实际消耗的 CPU 时间，纳秒
            clockLimit: 从启动到结束经过的真实墙钟时间，纳秒
            memoryLimit: 进程可使用的总内存，字节
            stackLimit: 线程栈空间，字节，不等同于总内存
            procLimit: 进程及子进程数量上限
        */
        if (compileLimit != null) {
            // 编译阶段限制 javac、jar 等工具的资源消耗。
            cmd.put("cpuLimit", millisToNanos(compileLimit.getCpuLimitMs()));
            cmd.put("clockLimit", millisToNanos(compileLimit.getRealCpuLimitMs()));
            cmd.put("memoryLimit", kilobytesToBytes(compileLimit.getMemoryLimitKb()));
            cmd.put("stackLimit", kilobytesToBytes(compileLimit.getStackLimitKb()));
            cmd.put("procLimit", compileLimit.getProcLimit());
        } else {
            // 运行阶段限制用户程序的资源消耗；墙钟时间适当放宽以覆盖 IO 等待。
            long cpuLimitNs = millisToNanos(runtimeLimit.getTimeLimit());
            long memoryLimitBytes = kilobytesToBytes(runtimeLimit.getMemoryLimit());
            cmd.put("cpuLimit", cpuLimitNs);
            cmd.put("clockLimit", cpuLimitNs * REAL_CPU_LIMIT_MULTIPLIER);
            cmd.put("memoryLimit", memoryLimitBytes);
            cmd.put("stackLimit", DEFAULT_STACK_LIMIT_BYTES);
            cmd.put("procLimit", DEFAULT_PROC_LIMIT);
        }

        cmd.put("files", List.of(stdin, stdout, stderr));
        cmd.put("copyIn", copyFileName);
        // stdout/stderr 会返回在本次响应中，编译产物则通过 copyOutCached 单独缓存。
        cmd.put("copyOut", List.of("stdout", "stderr"));

        if (copyOutCached != null && !copyOutCached.isBlank()) {
            cmd.put("copyOutCached", List.of(copyOutCached));
        }

        return JSONObject.of("cmd", List.of(cmd));
    }

    /**
     * 将 GOJ 业务层的毫秒转换为 go-judge 所需的纳秒。
     *
     * @param value 毫秒数
     * @return 纳秒数
     */
    private long millisToNanos(Integer value) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException("time limit must be non-negative");
        }
        return value.longValue() * 1_000_000L;
    }

    /**
     * 将 GOJ 业务层的 KB 转换为 go-judge 所需的字节数。
     *
     * @param value KB 数
     * @return 字节数
     */
    private long kilobytesToBytes(Integer value) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException("memory limit must be non-negative");
        }
        return value.longValue() * 1024L;
    }

    /**
     * 根据程序文件来源构造 go-judge copyIn。
     *
     * @param program 缓存产物或内联源码
     * @return go-judge copyIn 对象
     * @throws IllegalArgumentException 文件来源不满足互斥约束时抛出
     */
    protected JSONObject createProgramCopyIn(ProgramArtifact program) {
        boolean cached = program.getFileId() != null && !program.getFileId().isBlank();
        boolean inline = program.getContent() != null;

        if (cached == inline) {
            throw new IllegalArgumentException("程序必须且只能指定 fileId 或 content");
        }

        JSONObject file = cached
                ? JSONObject.of("fileId", program.getFileId())
                : JSONObject.of("content", program.getContent());

        return JSONObject.of(program.getActiveFileName(), file);
    }
}
