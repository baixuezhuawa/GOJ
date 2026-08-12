package com.gusl.gojjudge.adapter.lang;

import com.alibaba.fastjson2.JSONObject;
import com.gusl.gojjudge.adapter.AbstractLanguageAdapter;
import com.gusl.gojjudge.pojo.entity.CompilePlan;
import com.gusl.gojjudge.pojo.entity.RunContext;
import com.gusl.gojjudge.properties.lang.Java11Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Java 11 语言适配器。
 */
@Component
@RequiredArgsConstructor
public class Java11LanguageAdapter extends AbstractLanguageAdapter {

    /** Java 程序运行时标准输出上限，单位为字节。 */
    private static final int DEFAULT_RUN_STDOUT_LIMIT_BYTES = 1024 * 1024;

    /** Java 程序运行时标准错误输出上限，单位为字节。 */
    private static final int DEFAULT_RUN_STDERR_LIMIT_BYTES = 16 * 1024 * 1024;

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

    /**
     * 为 Java 源码创建编译计划。
     *
     * @param sourceCode 用户提交的 Java 源码
     * @return Java 编译请求及编译产物名称
     */
    @Override
    public CompilePlan createCompilePlan(String sourceCode) {
        return CompilePlan.builder()
                .required(true)
                .requestBody(createCompileRequest(sourceCode))
                .cachedArtifactName(java11.getActiveCacheFileName())
                .build();
    }

    /**
     * 构造 Java 编译请求。
     *
     * <p>源代码以 {@code Main.java} 写入沙箱，编译命令生成 {@code Main.jar}；
     * 该 jar 会被缓存，供后续所有测试点复用。</p>
     *
     * @param sourceCode 用户提交的 Java 源码
     * @return go-judge 编译请求体
     */
    @Override
    protected JSONObject createCompileRequest(String sourceCode) {
        JSONObject stdin = JSONObject.of("content", "");
        JSONObject stdout = JSONObject.of(
                "name", "stdout",
                "max", java11.getCompile().getStdoutLimitBytes()
        );
        JSONObject stderr = JSONObject.of(
                "name", "stderr",
                "max", java11.getCompile().getStderrLimitBytes()
        );
        JSONObject sourceFile = JSONObject.of("content", sourceCode);
        JSONObject copyIn = JSONObject.of("Main.java", sourceFile);

        // 编译请求携带编译限制，并要求沙箱返回 Main.jar 的缓存 fileId。
        return buildCommonRequest(
                buildCompileArgs(),
                java11.getEnv(),
                stdin,
                stdout,
                stderr,
                copyIn,
                java11.getCompile(),
                null,
                java11.getActiveCacheFileName()
        );
    }

    /**
     * 构造 Java 运行请求。
     *
     * @param runContext 当前测试点的输入、运行限制和编译产物信息
     * @return go-judge 运行请求体
     */
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
                java11.getEnv(),
                stdin,
                stdout,
                stderr,
                copyFileName,
                null,
                runContext.getLimit(),
                null
        );
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
                "%s -encoding UTF-8 Main.java && %s -cf Main.jar *.class",
                java11.getJavac(),
                java11.getJar()
        );
        return List.of("/bin/bash", "-c", command);
    }
}
