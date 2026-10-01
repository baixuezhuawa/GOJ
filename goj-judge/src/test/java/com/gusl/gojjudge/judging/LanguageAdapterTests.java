package com.gusl.gojjudge.judging;

import com.alibaba.fastjson2.JSONObject;
import com.gusl.common.constant.SandBoxStatus;
import com.gusl.gojjudge.judging.domain.exception.SystemErrorException;
import com.gusl.gojjudge.judging.infrastructure.compiler.CompileResult;
import com.gusl.gojjudge.judging.infrastructure.compiler.Compiler;
import com.gusl.gojjudge.judging.infrastructure.language.AbstractLanguageAdapter;
import com.gusl.gojjudge.judging.infrastructure.language.ConfigurableLanguageAdapter;
import com.gusl.gojjudge.judging.infrastructure.language.LanguageAdapterRegistry;
import com.gusl.gojjudge.judging.infrastructure.language.properties.LanguageConfig;
import com.gusl.gojjudge.judging.infrastructure.language.properties.LanguageProperties;
import com.gusl.gojjudge.judging.infrastructure.runner.RunContext;
import com.gusl.gojjudge.judging.infrastructure.sandbox.GoJudgeExecuter;
import com.gusl.gojjudge.judging.infrastructure.sandbox.SandBoxResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 语言配置集成测试，验证配置绑定、请求组装和注册表快照替换。
 */
class LanguageAdapterTests {

    /** 只启动配置绑定与注册表，不连接数据库、消息队列或沙箱。 */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LanguageProperties.class)
    @Import(LanguageAdapterRegistry.class)
    static class TestConfiguration {
    }

    /** 真实 application.yml 能直接驱动 Spring 创建两种语言的适配器。 */
    @Test
    void createsAdaptersFromApplicationYaml() throws IOException {
        PropertySource<?> source = applicationSource();
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfiguration.class)
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(source))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    LanguageAdapterRegistry registry = context.getBean(LanguageAdapterRegistry.class);
                    assertInstanceOf(ConfigurableLanguageAdapter.class, registry.require("java11"));
                    assertInstanceOf(ConfigurableLanguageAdapter.class, registry.require("py3"));
                    assertTrue(registry.require("java11").isNeedCompile());
                    assertFalse(registry.require("py3").isNeedCompile());
                });
    }

    /** 没有专属 Java 类的新语言，仅凭配置也能注册并构造运行请求。 */
    @Test
    void createsNewLanguageUsingOnlyConfiguration() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues(
                        "goj.judge.languages[0].code=node22",
                        "goj.judge.languages[0].source-file-name=solution.js",
                        "goj.judge.languages[0].need-compile=false",
                        "goj.judge.languages[0].run-args[0]=/usr/bin/node",
                        "goj.judge.languages[0].run-args[1]=/w/solution.js",
                        "goj.judge.languages[0].run.clock-limit-multiplier=4",
                        "goj.judge.languages[0].run.stack-limit-kb=8192",
                        "goj.judge.languages[0].run.proc-limit=32",
                        "goj.judge.languages[0].run.stdout-limit-bytes=1024",
                        "goj.judge.languages[0].run.stderr-limit-bytes=2048")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    AbstractLanguageAdapter adapter = context.getBean(LanguageAdapterRegistry.class).require("node22");
                    JSONObject request = command(adapter.createRunRequest(runContext(JSONObject.of("content", "console.log(1)"))));
                    assertEquals(List.of("/usr/bin/node", "/w/solution.js"), request.getJSONArray("args"));
                    assertEquals("console.log(1)", request.getJSONObject("copyIn").getJSONObject("solution.js").getString("content"));
                    assertEquals(4000_000_000L, request.getLongValue("realCpuLimit"));
                });
    }

    /** Java 编译请求保持源码、编译打包命令和缓存产物约定，正确换算限制。 */
    @Test
    void buildsJavaCompileRequest() throws IOException {
        AbstractLanguageAdapter adapter = new LanguageAdapterRegistry(properties()).require("java11");
        String sourceCode = "public class Main {}";
        JSONObject request = command(adapter.createCompileRequest(sourceCode));

        assertEquals(List.of("/bin/bash", "-c",
                "/usr/local/java/jdk11/bin/javac -encoding UTF-8 Main.java && /usr/local/java/jdk11/bin/jar -cf Main.jar *.class"),
                request.getJSONArray("args"));
        assertEquals(sourceCode, request.getJSONObject("copyIn").getJSONObject("Main.java").getString("content"));
        assertEquals(List.of("Main.jar"), request.getJSONArray("copyOutCached"));
        assertEquals(10000_000_000L, request.getLongValue("cpuLimit"));
        assertEquals(30000_000_000L, request.getLongValue("realCpuLimit"));
        assertEquals(536870912L, request.getLongValue("memoryLimit"));
        assertEquals(134217728L, request.getLongValue("stackLimit"));
        assertEquals("", request.getJSONArray("files").getJSONObject(0).getString("content"));
        assertEquals(16777216, request.getJSONArray("files").getJSONObject(1).getIntValue("max"));
    }

    /** 编译产物经原 Compiler 调用传给运行请求，最终按缓存 ID 清理。 */
    @Test
    void passesCompiledArtifactThroughExistingCompiler() throws IOException {
        AbstractLanguageAdapter adapter = new LanguageAdapterRegistry(properties()).require("java11");
        GoJudgeExecuter executer = mock(GoJudgeExecuter.class);
        SandBoxResult response = new SandBoxResult();
        response.setStatus(SandBoxStatus.ACCEPTED);
        response.setFileIds(Map.of("Main.jar", "cache-123"));
        response.setFiles(Map.of("stdout", "", "stderr", ""));
        when(executer.execute(any())).thenReturn(response);
        Compiler compiler = new Compiler(executer);

        CompileResult compiled = compiler.compile("public class Main {}", adapter);
        JSONObject request = command(adapter.createRunRequest(runContext(compiled.getActiveFile())));

        assertEquals(SandBoxStatus.ACCEPTED, compiled.getStatus());
        assertEquals("cache-123", request.getJSONObject("copyIn").getJSONObject("Main.jar").getString("fileId"));
        assertEquals("2 3\n", request.getJSONArray("files").getJSONObject(0).getString("content"));
        assertEquals(List.of("/usr/local/java/jdk11/bin/java", "-Dfile.encoding=UTF-8", "-cp", "/w/Main.jar", "Main"),
                request.getJSONArray("args"));
        assertEquals(List.of("PATH=/usr/local/java/jdk11/bin:/usr/bin:/bin"), request.getJSONArray("env"));
        assertEquals(67108864L, request.getLongValue("memoryLimit"));
        assertEquals(3000_000_000L, request.getLongValue("realCpuLimit"));
        compiler.deleteCacheCompileFile(compiled);
        verify(executer).deleteCacheFile("cache-123");
    }

    /** 脚本跳过沙箱编译，运行时源码和测试输入分别挂载，且无需清理缓存。 */
    @Test
    void keepsPythonSourceSeparateFromTestInput() throws IOException {
        AbstractLanguageAdapter adapter = new LanguageAdapterRegistry(properties()).require("py3");
        GoJudgeExecuter executer = mock(GoJudgeExecuter.class);
        Compiler compiler = new Compiler(executer);
        String sourceCode = "print(sum(map(int, input().split())))";

        CompileResult compiled = compiler.compile(sourceCode, adapter);
        JSONObject request = command(adapter.createRunRequest(runContext(compiled.getActiveFile())));

        assertEquals(SandBoxStatus.ACCEPTED, compiled.getStatus());
        assertEquals(sourceCode, request.getJSONObject("copyIn").getJSONObject("Main.py").getString("content"));
        assertEquals("2 3\n", request.getJSONArray("files").getJSONObject(0).getString("content"));
        assertEquals(List.of("/usr/bin/python3", "/w/Main.py"), request.getJSONArray("args"));
        assertEquals(List.of("PYTHONIOENCODING=utf-8"), request.getJSONArray("env"));
        assertFalse(request.containsKey("copyOutCached"));
        compiler.deleteCacheCompileFile(compiled);
        verifyNoInteractions(executer);
    }

    /** 新旧任务各用各的快照，外部修改配置对象和请求 JSON 不会污染适配器。 */
    @Test
    void replacesRegistryWithoutMutatingExistingAdapters() throws IOException {
        LanguageProperties properties = properties();
        LanguageAdapterRegistry registry = new LanguageAdapterRegistry(properties);
        AbstractLanguageAdapter old = registry.require("java11");
        LanguageConfig config = properties.getLanguages().get(0);
        config.getRunArgs().set(0, "/new/java");
        config.getEnv().set(0, "PATH=/new");
        config.getRun().setClockLimitMultiplier(5);
        config.getCompile().setCpuLimitMs(20000);
        registry.reload(properties.getLanguages());
        AbstractLanguageAdapter current = registry.require("java11");
        config.getRunArgs().set(0, "/later/java");
        config.getRun().setClockLimitMultiplier(9);

        JSONObject oldRequest = command(old.createRunRequest(runContext(JSONObject.of("fileId", "old"))));
        JSONObject newRequest = command(current.createRunRequest(runContext(JSONObject.of("fileId", "new"))));
        assertEquals("/usr/local/java/jdk11/bin/java", oldRequest.getJSONArray("args").getString(0));
        assertEquals(3000_000_000L, oldRequest.getLongValue("realCpuLimit"));
        assertEquals("/new/java", newRequest.getJSONArray("args").getString(0));
        assertEquals(5000_000_000L, newRequest.getLongValue("realCpuLimit"));
        assertEquals(10000_000_000L, command(old.createCompileRequest("code")).getLongValue("cpuLimit"));
        assertEquals(20000_000_000L, command(current.createCompileRequest("code")).getLongValue("cpuLimit"));
        newRequest.getJSONArray("args").set(0, "/request/java");
        assertEquals("/new/java", command(current.createRunRequest(runContext(JSONObject.of("fileId", "new"))))
                .getJSONArray("args").getString(0));
    }

    /** 更新包含无效语言时，连已经构建成功的其他语言也不会部分发布。 */
    @Test
    void retainsWholeRegistryWhenReloadFails() throws IOException {
        LanguageProperties properties = properties();
        LanguageAdapterRegistry registry = new LanguageAdapterRegistry(properties);
        AbstractLanguageAdapter old = registry.require("java11");
        properties.getLanguages().get(0).setCode("java-new");
        properties.getLanguages().get(1).getRun().setProcLimit(0);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> registry.reload(properties.getLanguages()));
        assertTrue(error.getMessage().contains("py3"));
        assertTrue(error.getMessage().contains("run.proc-limit"));
        assertSame(old, registry.require("java11"));
        assertNotNull(registry.require("py3"));
        assertThrows(SystemErrorException.class, () -> registry.require("java-new"));
    }

    /** 重复编码不能覆盖已注册语言。 */
    @Test
    void rejectsDuplicateLanguageCodes() throws IOException {
        LanguageProperties properties = properties();
        LanguageAdapterRegistry registry = new LanguageAdapterRegistry(properties);
        AbstractLanguageAdapter old = registry.require("java11");
        properties.getLanguages().get(1).setCode("java11");
        assertThrows(IllegalArgumentException.class, () -> registry.reload(properties.getLanguages()));
        assertSame(old, registry.require("java11"));
    }

    /** 删除语言仅影响后续查询，正在执行的任务仍能构造请求。 */
    @Test
    void removalKeepsPreviouslyAcquiredAdapterUsable() throws IOException {
        LanguageAdapterRegistry registry = new LanguageAdapterRegistry(properties());
        AbstractLanguageAdapter old = registry.require("py3");
        registry.reload(List.of());

        assertThrows(SystemErrorException.class, () -> registry.require("py3"));
        JSONObject request = command(old.createRunRequest(runContext(JSONObject.of("content", "print(1)"))));
        assertEquals("print(1)", request.getJSONObject("copyIn").getJSONObject("Main.py").getString("content"));
    }

    /** 编译型语言缺少编译配置时立即拒绝，避免到任务执行时才失败。 */
    @Test
    void rejectsMissingCompileConfiguration() throws IOException {
        LanguageConfig config = properties().getLanguages().get(0);
        config.setCompile(null);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ConfigurableLanguageAdapter(config));
        assertTrue(error.getMessage().contains("java11"));
        assertTrue(error.getMessage().contains("compile"));
    }

    /** 读取实际配置文件，避免测试另建一套与生产漂移的语言参数。 */
    private PropertySource<?> applicationSource() throws IOException {
        return new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml")).get(0);
    }

    /** 使用 Spring Boot 的绑定器还原 YAML 中的配置集合。 */
    private LanguageProperties properties() throws IOException {
        return new Binder(ConfigurationPropertySources.from(applicationSource()))
                .bind("goj.judge", LanguageProperties.class).get();
    }

    /** 提供与题目限制单位一致的测试点上下文。 */
    private RunContext runContext(JSONObject activeFile){
        return RunContext.builder().input("2 3\n").timeLimitMs(1000L)
                .memoryLimitKb(65536L).activeFile(activeFile).build();
    }

    /** 取出单次 go-judge 请求的命令对象。 */
    private JSONObject command(JSONObject request){
        return request.getJSONArray("cmd").getJSONObject(0);
    }
}
