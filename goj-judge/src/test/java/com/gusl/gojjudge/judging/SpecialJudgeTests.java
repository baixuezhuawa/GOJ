package com.gusl.gojjudge.judging;

import com.alibaba.fastjson2.JSONObject;
import com.gusl.common.constant.CheckType;
import com.gusl.common.constant.ProblemType;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.constant.SandBoxStatus;
import com.gusl.common.pojo.entity.JudgeTaskMessage;
import com.gusl.gojjudge.judging.engine.JudgeEngine;
import com.gusl.gojjudge.judging.engine.JudgeExecutionRequest;
import com.gusl.gojjudge.judging.engine.JudgeSession;
import com.gusl.gojjudge.judging.checker.ExactOutputChecker;
import com.gusl.gojjudge.judging.checker.SpecialOutputChecker;
import com.gusl.gojjudge.judging.exception.JudgeSystemException;
import com.gusl.gojjudge.judging.model.CheckerConfig;
import com.gusl.gojjudge.judging.model.TestDataRef;
import com.gusl.gojjudge.judging.compiler.CompileResult;
import com.gusl.gojjudge.judging.compiler.Compiler;
import com.gusl.gojjudge.judging.language.AbstractLanguageAdapter;
import com.gusl.gojjudge.judging.language.LanguageAdapterRegistry;
import com.gusl.gojjudge.judging.runner.RunContext;
import com.gusl.gojjudge.judging.runner.RunResult;
import com.gusl.gojjudge.judging.runner.Runner;
import com.gusl.gojjudge.judging.testcase.LocalFileTestCaseProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 验证特殊判题链路的输出传递、普通题回归、系统故障和编译产物清理。 */
class SpecialJudgeTests {

    @TempDir
    Path dataRoot;

    private final Compiler compiler = mock(Compiler.class);
    private final Runner runner = mock(Runner.class);
    private final AbstractLanguageAdapter solutionAdapter = mock(AbstractLanguageAdapter.class);
    private final AbstractLanguageAdapter checkerAdapter = mock(AbstractLanguageAdapter.class);
    private final CompileResult solutionCompiled = compiled("选手编译产物");
    private final CompileResult checkerCompiled = compiled("校验编译产物");
    private JudgeEngine engine;
    private JudgeExecutionRequest request;
    private JudgeSession session;

    @BeforeEach
    void prepare() throws IOException {
        // 使用真实文件加载器和校验器，仅替换沙箱编译、运行边界。
        for (int caseNo = 1; caseNo <= 2; caseNo++) {
            Path casePath = Files.createDirectories(dataRoot.resolve("cases/test" + caseNo));
            Files.writeString(casePath.resolve("input.txt"), "3\n");
            Files.writeString(casePath.resolve("output.txt"), "1 2 3\n");
        }
        LocalFileTestCaseProvider provider = new LocalFileTestCaseProvider();
        ReflectionTestUtils.setField(provider, "dataRoot", dataRoot.toString());
        ExactOutputChecker exact = new ExactOutputChecker();

        LanguageAdapterRegistry languages = mock(LanguageAdapterRegistry.class);
        when(languages.require("solution")).thenReturn(solutionAdapter);
        when(languages.require("checker")).thenReturn(checkerAdapter);
        when(compiler.compile("选手代码", solutionAdapter)).thenReturn(solutionCompiled);
        when(compiler.compile("校验代码", checkerAdapter)).thenReturn(checkerCompiled);
        engine = new JudgeEngine(languages, compiler, runner, provider, exact, new SpecialOutputChecker(runner, exact));

        CheckerConfig config = new CheckerConfig();
        config.setCheckType(CheckType.SPECIAL);
        config.setLanguage("checker");
        config.setSourceCode("校验代码");
        request = JudgeExecutionRequest.builder().language("solution").sourceCode("选手代码")
                .timeLimitMs(1000L).memoryLimitKb(262144L)
                .problemType(ProblemType.SPECIAL).checkerConfig(config)
                .testDataRef(TestDataRef.builder().storagePath("cases").testCaseCount(2).build()).build();
        session = new JudgeSession(new JudgeTaskMessage(), (task, outcome) -> true);
        session.setRequest(request);
        // 正式入口 JudgeServiceImpl 会先写入 WAIT，再调用引擎。
        session.status(JudgingConstant.WAIT).push();
    }

    @Test
    void acceptsDifferentAnswersAfterCheckerAndReusesCompilation() {
        when(runner.run(eq(solutionAdapter), any())).thenReturn(run("3 1 2\n"), run("2 3 1\n"));
        when(runner.run(eq(checkerAdapter), any())).thenReturn(run("1 2 3\n"));

        engine.judge(session);

        assertEquals(JudgingConstant.ACCEPTED, session.getPending().getCurStatus());
        assertTrue(session.isFinished());
        ArgumentCaptor<RunContext> captured = ArgumentCaptor.forClass(RunContext.class);
        verify(runner, times(2)).run(eq(checkerAdapter), captured.capture());
        assertEquals("3 1 2\n", captured.getAllValues().get(0).getInput());
        assertEquals("2 3 1\n", captured.getAllValues().get(1).getInput());
        assertSame(checkerCompiled.getActiveFile(), captured.getValue().getActiveFile());
        verify(compiler).compile("校验代码", checkerAdapter);
        verify(compiler).deleteCacheCompileFile(checkerCompiled);
        verify(compiler).deleteCacheCompileFile(solutionCompiled);
    }

    @Test
    void mismatchedCheckerOutputIsWrongAnswerAndStopsRemainingCases() {
        when(runner.run(eq(solutionAdapter), any())).thenReturn(run("1 1 3\n"));
        when(runner.run(eq(checkerAdapter), any())).thenReturn(run("1 1 3\n"));

        engine.judge(session);

        assertEquals(JudgingConstant.WRONG_ANSWER, session.getPending().getCurStatus());
        verify(runner).run(eq(solutionAdapter), any());
        verify(runner).run(eq(checkerAdapter), any());
    }

    @Test
    void normalProblemStillComparesOriginalOutput() {
        request.setProblemType(ProblemType.NORMAL);
        request.getCheckerConfig().setCheckType(CheckType.TEXT);
        when(runner.run(eq(solutionAdapter), any())).thenReturn(run("3 1 2\n"));

        engine.judge(session);

        assertEquals(JudgingConstant.WRONG_ANSWER, session.getPending().getCurStatus());
        verify(compiler, never()).compile("校验代码", checkerAdapter);
        verify(runner, never()).run(eq(checkerAdapter), any());
    }

    @Test
    void checkerCompilationFailureIsSystemFailureAndCleansArtifacts() {
        checkerCompiled.setStatus(SandBoxStatus.NONZERO_EXIT_STATUS);
        checkerCompiled.setCompileMsg("语法错误");

        assertThrows(JudgeSystemException.class, () -> engine.judge(session));

        assertFalse(session.isFinished());
        verifyNoInteractions(runner);
        verify(compiler).deleteCacheCompileFile(checkerCompiled);
        verify(compiler).deleteCacheCompileFile(solutionCompiled);
    }

    @Test
    void checkerRuntimeFailureDoesNotBecomeContestantWrongAnswer() {
        when(runner.run(eq(solutionAdapter), any())).thenReturn(run("3 2 1\n"));
        RunResult failure = run("");
        failure.setStatus(SandBoxStatus.TIME_LIMIT_EXCEEDED);
        when(runner.run(eq(checkerAdapter), any())).thenReturn(failure);

        RuntimeException error = assertThrows(RuntimeException.class, () -> engine.judge(session));

        assertTrue(error.getMessage().contains("校验程序运行失败"));
        assertFalse(session.isFinished());
        assertEquals(JudgingConstant.RUNNING, session.getPending().getCurStatus());
        verify(compiler).deleteCacheCompileFile(checkerCompiled);
        verify(compiler).deleteCacheCompileFile(solutionCompiled);
    }

    /** 选手编译失败正常结束为 CE，不启动选手或校验程序。 */
    @Test
    void solutionCompilationFailureFinishesWithCe() {
        solutionCompiled.setStatus(SandBoxStatus.NONZERO_EXIT_STATUS);
        engine.judge(session);
        assertTrue(session.isFinished());
        assertEquals(JudgingConstant.COMPILE_ERROR, session.getPending().getCurStatus());
        verifyNoInteractions(runner);
        verify(compiler, never()).compile("校验代码", checkerAdapter);
        verify(compiler).deleteCacheCompileFile(solutionCompiled);
    }

    /** 交互题仍明确拒绝执行，不进入编译。 */
    @Test
    void interactiveProblemRemainsUnsupported() {
        request.setProblemType(ProblemType.INTERACTION);
        assertTrue(assertThrows(JudgeSystemException.class, () -> engine.judge(session))
                .getMessage().contains("暂不支持交互题"));
        assertFalse(session.isFinished());
        verifyNoInteractions(compiler, runner);
    }

    /** 最终回写被拒绝时不能宣布完成，且仍需清理两个编译产物。 */
    @Test
    void rejectedFinalWriteDoesNotFinishSession() {
        session.setWriter((task, outcome) -> !JudgingConstant.TERMINAL_STATUSES.contains(outcome.getCurStatus()));
        when(runner.run(eq(solutionAdapter), any())).thenReturn(run("3 1 2\n"));
        when(runner.run(eq(checkerAdapter), any())).thenReturn(run("1 2 3\n"));
        assertThrows(JudgeSystemException.class, () -> engine.judge(session));
        assertFalse(session.isFinished());
        verify(compiler).deleteCacheCompileFile(checkerCompiled);
        verify(compiler).deleteCacheCompileFile(solutionCompiled);
    }

    /** 构造编译成功且具有独立缓存标识的结果。 */
    private static CompileResult compiled(String fileId) {
        CompileResult result = new CompileResult();
        result.setStatus(SandBoxStatus.ACCEPTED);
        result.setActiveFile(JSONObject.of("fileId", fileId));
        return result;
    }

    /** 构造具有完整资源统计的正常运行结果。 */
    private RunResult run(String output) {
        RunResult result = new RunResult();
        result.setStatus(SandBoxStatus.ACCEPTED);
        result.setExitStatus(0);
        result.setStdout(output);
        result.setRunTimeNan(1_000_000L);
        result.setMemoryByte(1024L);
        return result;
    }
}
