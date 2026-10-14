package com.gusl.gojjudge.judging;

import com.alibaba.fastjson2.JSONObject;
import com.gusl.common.constant.CheckType;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.constant.SandBoxStatus;
import com.gusl.common.pojo.entity.JudgeTaskMessage;
import com.gusl.gojjudge.judging.application.engine.JudgeEngine;
import com.gusl.gojjudge.judging.application.engine.JudgeExecutionRequest;
import com.gusl.gojjudge.judging.application.engine.JudgeSession;
import com.gusl.gojjudge.judging.application.strategy.JudgeStrategy;
import com.gusl.gojjudge.judging.application.strategy.NormalJudgeStrategy;
import com.gusl.gojjudge.judging.application.strategy.SpecialJudgeStrategy;
import com.gusl.gojjudge.judging.domain.checker.OutputChecker;
import com.gusl.gojjudge.judging.domain.exception.SystemErrorException;
import com.gusl.gojjudge.judging.domain.model.CheckerConfig;
import com.gusl.gojjudge.judging.domain.model.TestCase;
import com.gusl.gojjudge.judging.domain.model.TestDataRef;
import com.gusl.gojjudge.judging.infrastructure.checker.OutputCheckerRegistry;
import com.gusl.gojjudge.judging.infrastructure.compiler.CompileResult;
import com.gusl.gojjudge.judging.infrastructure.compiler.Compiler;
import com.gusl.gojjudge.judging.infrastructure.language.AbstractLanguageAdapter;
import com.gusl.gojjudge.judging.infrastructure.language.LanguageAdapterRegistry;
import com.gusl.gojjudge.judging.infrastructure.runner.RunResult;
import com.gusl.gojjudge.judging.infrastructure.runner.Runner;
import com.gusl.gojjudge.judging.infrastructure.testcase.LocalFileTestCaseProvider;
import com.gusl.gojjudge.judging.infrastructure.testcase.TestCaseReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 沙箱故障回归测试，验证两个策略不会将系统故障写成选手终态。 */
class SandboxFailureTests {

    private final Runner runner = mock(Runner.class);
    private final Compiler compiler = mock(Compiler.class);
    private final LanguageAdapterRegistry languages = mock(LanguageAdapterRegistry.class);
    private final OutputCheckerRegistry checkers = mock(OutputCheckerRegistry.class);
    private final OutputChecker checker = mock(OutputChecker.class);
    private final LocalFileTestCaseProvider provider = mock(LocalFileTestCaseProvider.class);
    private final CompileResult compiled = new CompileResult();
    private final List<String> writtenStatuses = new ArrayList<>();
    private JudgeSession session;

    /** 保留真实引擎和策略，替换编译、运行和数据读取边界。 */
    @BeforeEach
    void prepare() {
        when(languages.require(any())).thenReturn(mock(AbstractLanguageAdapter.class));
        compiled.setStatus(SandBoxStatus.ACCEPTED);
        compiled.setActiveFile(JSONObject.of("content", "程序"));
        when(compiler.compile(any(), any())).thenReturn(compiled);
        when(checkers.require(any())).thenReturn(checker);
        TestCaseReader reader = mock(TestCaseReader.class);
        when(provider.open(any())).thenReturn(reader);
        when(reader.read(1)).thenReturn(new TestCase(1, "1 8 3", "8"));
    }

    /** 普通策略遇到两种沙箱故障时抛异常，即使没有资源统计也保留故障状态。 */
    @Test
    void normalStrategyPropagatesSandboxFailures() {
        verifySandboxFailures(new NormalJudgeStrategy(runner, checkers, provider), CheckType.TEXT);
    }

    /** 特殊策略使用相同的系统故障边界，不继续运行校验器。 */
    @Test
    void specialStrategyPropagatesSandboxFailures() {
        verifySandboxFailures(new SpecialJudgeStrategy(runner, checkers, provider), CheckType.SPECIAL);
    }

    /** 沙箱正常报告的选手错误仍按 RE、TLE、MLE 写回。 */
    @Test
    void contestantFailuresKeepTheirOriginalVerdicts() {
        List<JudgeStrategy> strategies = List.of(
                new NormalJudgeStrategy(runner, checkers, provider),
                new SpecialJudgeStrategy(runner, checkers, provider));
        List<String> statuses = List.of(SandBoxStatus.SIGNALLED, SandBoxStatus.NONZERO_EXIT_STATUS,
                SandBoxStatus.TIME_LIMIT_EXCEEDED, SandBoxStatus.MEMORY_LIMIT_EXCEEDED);
        List<String> verdicts = List.of(JudgingConstant.RUNTIME_ERROR, JudgingConstant.RUNTIME_ERROR,
                JudgingConstant.TIME_LIMIT_EXCEEDED, JudgingConstant.MEMORY_LIMIT_EXCEEDED);
        for (JudgeStrategy strategy : strategies) {
            for (int index = 0; index < statuses.size(); index++) {
                when(runner.run(any(), any())).thenReturn(runResult(statuses.get(index), true));

                judge(strategy, CheckType.TEXT);

                assertTrue(session.isFinished());
                assertEquals(verdicts.get(index), session.getPending().getCurStatus());
            }
        }
        verifyNoInteractions(checker);
    }

    /** 验证异常、会话状态、编译产物清理及未执行后续测试点。 */
    private void verifySandboxFailures(JudgeStrategy strategy, CheckType checkType) {
        for (String status : List.of(SandBoxStatus.INTERNAL_ERROR, SandBoxStatus.FILE_ERROR)) {
            for (boolean hasMetrics : List.of(true, false)) {
                clearInvocations(compiler, runner);
                when(runner.run(any(), any())).thenReturn(runResult(status, hasMetrics));

                SystemErrorException error = assertThrows(SystemErrorException.class,
                        () -> judge(strategy, checkType));

                assertTrue(error.getMessage().contains(status));
                assertFalse(session.isFinished());
                assertEquals(JudgingConstant.RUNNING, session.getPending().getCurStatus());
                assertTrue(writtenStatuses.stream().noneMatch(JudgingConstant.TERMINAL_STATUSES::contains));
                verify(runner).run(any(), any());
                verify(compiler, times(checkType == CheckType.SPECIAL ? 2 : 1)).deleteCacheCompileFile(compiled);
                verifyNoInteractions(checker);
            }
        }
    }

    /** 从正式调用入口的 WAIT 状态进入引擎，记录实际写回的状态。 */
    private void judge(JudgeStrategy strategy, CheckType checkType) {
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
                .executionMode(strategy.executionMode()).checkerConfig(config)
                .testDataRef(TestDataRef.builder().testCaseCount(2).build()).build();
        new JudgeEngine(languages, List.of(strategy), compiler).judge(request, session);
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
