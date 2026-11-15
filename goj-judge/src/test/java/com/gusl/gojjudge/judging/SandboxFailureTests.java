package com.gusl.gojjudge.judging;

import com.alibaba.fastjson2.JSONObject;
import com.gusl.common.constant.CheckType;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.constant.SandBoxStatus;
import com.gusl.common.pojo.entity.JudgeTaskMessage;
import com.gusl.gojjudge.judging.engine.JudgeEngine;
import com.gusl.gojjudge.judging.engine.JudgeExecutionRequest;
import com.gusl.gojjudge.judging.engine.JudgeSession;
import com.gusl.gojjudge.judging.checker.ExactOutputChecker;
import com.gusl.gojjudge.judging.checker.SpecialOutputChecker;
import com.gusl.common.constant.ProblemType;
import com.gusl.gojjudge.judging.exception.SystemErrorException;
import com.gusl.gojjudge.judging.model.CheckerConfig;
import com.gusl.gojjudge.judging.model.TestCase;
import com.gusl.gojjudge.judging.model.TestDataRef;
import com.gusl.gojjudge.judging.compiler.CompileResult;
import com.gusl.gojjudge.judging.compiler.Compiler;
import com.gusl.gojjudge.judging.language.AbstractLanguageAdapter;
import com.gusl.gojjudge.judging.language.LanguageAdapterRegistry;
import com.gusl.gojjudge.judging.runner.RunResult;
import com.gusl.gojjudge.judging.runner.Runner;
import com.gusl.gojjudge.judging.testcase.LocalFileTestCaseProvider;
import com.gusl.gojjudge.judging.testcase.TestCaseReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 沙箱故障回归测试，验证普通题和特殊题不会将系统故障写成选手终态。 */
class SandboxFailureTests {

    private final Runner runner = mock(Runner.class);
    private final Compiler compiler = mock(Compiler.class);
    private final LanguageAdapterRegistry languages = mock(LanguageAdapterRegistry.class);
    private final ExactOutputChecker checker = mock(ExactOutputChecker.class);
    private final SpecialOutputChecker specialChecker = mock(SpecialOutputChecker.class);
    private final LocalFileTestCaseProvider provider = mock(LocalFileTestCaseProvider.class);
    private final CompileResult compiled = new CompileResult();
    private final List<String> writtenStatuses = new ArrayList<>();
    private JudgeSession session;

    /** 保留真实引擎，替换编译、运行和数据读取边界。 */
    @BeforeEach
    void prepare() {
        when(languages.require(any())).thenReturn(mock(AbstractLanguageAdapter.class));
        compiled.setStatus(SandBoxStatus.ACCEPTED);
        compiled.setActiveFile(JSONObject.of("content", "程序"));
        when(compiler.compile(any(), any())).thenReturn(compiled);
        TestCaseReader reader = mock(TestCaseReader.class);
        when(provider.open(any())).thenReturn(reader);
        when(reader.read(1)).thenReturn(new TestCase(1, "1 8 3", "8"));
    }

    /** 普通题遇到两种沙箱故障时抛异常，即使没有资源统计也保留故障状态。 */
    @Test
    void normalStrategyPropagatesSandboxFailures() {
        verifySandboxFailures(CheckType.TEXT);
    }

    /** 特殊题使用相同的系统故障边界，不继续运行校验器。 */
    @Test
    void specialStrategyPropagatesSandboxFailures() {
        verifySandboxFailures(CheckType.SPECIAL);
    }

    /** 沙箱正常报告的选手错误仍按 RE、TLE、MLE 写回。 */
    @Test
    void contestantFailuresKeepTheirOriginalVerdicts() {
        List<String> statuses = List.of(SandBoxStatus.SIGNALLED, SandBoxStatus.NONZERO_EXIT_STATUS,
                SandBoxStatus.TIME_LIMIT_EXCEEDED, SandBoxStatus.MEMORY_LIMIT_EXCEEDED);
        List<String> verdicts = List.of(JudgingConstant.RUNTIME_ERROR, JudgingConstant.RUNTIME_ERROR,
                JudgingConstant.TIME_LIMIT_EXCEEDED, JudgingConstant.MEMORY_LIMIT_EXCEEDED);
        for (CheckType checkType : List.of(CheckType.TEXT, CheckType.SPECIAL)) {
            for (int index = 0; index < statuses.size(); index++) {
                when(runner.run(any(), any())).thenReturn(runResult(statuses.get(index), true));

                judge(checkType);

                assertTrue(session.isFinished());
                assertEquals(verdicts.get(index), session.getPending().getCurStatus());
            }
        }
        verifyNoInteractions(checker, specialChecker);
    }

    /** 验证异常、会话状态、编译产物清理及未执行后续测试点。 */
    private void verifySandboxFailures(CheckType checkType) {
        for (String status : List.of(SandBoxStatus.INTERNAL_ERROR, SandBoxStatus.FILE_ERROR)) {
            for (boolean hasMetrics : List.of(true, false)) {
                clearInvocations(compiler, runner);
                when(runner.run(any(), any())).thenReturn(runResult(status, hasMetrics));

                SystemErrorException error = assertThrows(SystemErrorException.class,
                        () -> judge(checkType));

                assertTrue(error.getMessage().contains(status));
                assertFalse(session.isFinished());
                assertEquals(JudgingConstant.RUNNING, session.getPending().getCurStatus());
                assertTrue(writtenStatuses.stream().noneMatch(JudgingConstant.TERMINAL_STATUSES::contains));
                verify(runner).run(any(), any());
                verify(compiler, times(checkType == CheckType.SPECIAL ? 2 : 1)).deleteCacheCompileFile(compiled);
                verifyNoInteractions(checker, specialChecker);
            }
        }
    }

    /** 从正式调用入口的 WAIT 状态进入引擎，记录实际写回的状态。 */
    private void judge(CheckType checkType) {
        writtenStatuses.clear();
        session = new JudgeSession(new JudgeTaskMessage(), (task, outcome) -> {
            writtenStatuses.add(outcome.getCurStatus());
            return true;
        });
        session.status(JudgingConstant.WAIT).push();
        CheckerConfig config = new CheckerConfig();
        config.setCheckType(checkType);
        config.setLanguage("py3");
        config.setSourceCode("校验代码");
        JudgeExecutionRequest request = JudgeExecutionRequest.builder()
                .language("py3").sourceCode("选手代码").timeLimitMs(1000L).memoryLimitKb(131072L)
                .problemType(checkType == CheckType.TEXT ? ProblemType.NORMAL : ProblemType.SPECIAL).checkerConfig(config)
                .testDataRef(TestDataRef.builder().testCaseCount(2).build()).build();
        session.setRequest(request);
        new JudgeEngine(languages, compiler, runner, provider, checker, specialChecker).judge(session);
    }

    /** 构造沙箱状态，系统故障允许缺少耗时和内存信息。 */
    private RunResult runResult(String status, boolean hasMetrics) {
        RunResult result = new RunResult();
        result.setStatus(status);
        result.setExitStatus(1);
        if (hasMetrics) {
            result.setRunTimeNan(1_000_000L);
            result.setMemoryByte(1024L);
        }
        return result;
    }
}
