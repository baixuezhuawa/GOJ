package com.gusl.gojjudge.judging.engine;

import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.constant.ProblemType;
import com.gusl.common.constant.SandBoxStatus;
import com.gusl.gojjudge.judging.checker.ExactOutputChecker;
import com.gusl.gojjudge.judging.checker.SpecialOutputChecker;
import com.gusl.gojjudge.judging.checker.OutputChecker;
import com.gusl.gojjudge.judging.exception.JudgeSystemException;
import com.gusl.gojjudge.judging.exception.SystemErrorException;
import com.gusl.gojjudge.judging.model.*;
import com.gusl.gojjudge.judging.compiler.CompileResult;
import com.gusl.gojjudge.judging.compiler.Compiler;
import com.gusl.gojjudge.judging.language.AbstractLanguageAdapter;
import com.gusl.gojjudge.judging.language.LanguageAdapterRegistry;
import com.gusl.gojjudge.judging.runner.RunContext;
import com.gusl.gojjudge.judging.runner.RunResult;
import com.gusl.gojjudge.judging.runner.Runner;
import com.gusl.gojjudge.judging.testcase.LocalFileTestCaseProvider;
import com.gusl.gojjudge.judging.testcase.TestCaseReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 测评引擎，直接组织编译、测试点运行、答案校验和结果回写。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeEngine {

    // 语言适配器注册器
    private final LanguageAdapterRegistry languageAdapterRegistry;

    // 编译/运行
    private final Compiler compiler;
    private final Runner runner;

    // 测试数据加载器 TODO 后续可以扩展成一个服务用于, 比赛系统的hack功能, 统一管理上传的hack数据
    private final LocalFileTestCaseProvider fileTestCaseProvider;

    // 答案检查器
    private final ExactOutputChecker exactOutputChecker;
    private final SpecialOutputChecker specialOutputChecker;

    /** 按题目类型执行判题，无论成功或异常均清理本次编译产物。 */
    public void judge(JudgeSession session) {
        try {
            Integer problemType = session.getRequest().getProblemType();

            // 交互
            if (ProblemType.INTERACTION.equals(problemType)) {
                interactiveJudge();
                return;
            }

            if (!ProblemType.NORMAL.equals(problemType) && !ProblemType.SPECIAL.equals(problemType)) {
                throw new JudgeSystemException("不支持的题目类型：" + problemType);
            }

            // 两种题目共用选手程序编译；CE 已写入终态，无需继续运行。
            if (!compileSolution(session)) {
                return;
            }

            JudgeResult result = ProblemType.NORMAL.equals(problemType) ? normalJudge(session) : specialJudge(session);

            if (!JudgingConstant.TERMINAL_STATUSES.contains(result.getStatus())) {
                throw new JudgeSystemException("判题结束但未得到终态，请检查测试数据");
            }

            // 业务回写成功后，Session 才能标记本次执行已完成。
            session.status(result.getStatus())
                    .usage(result.getTimeMs(), result.getMemoryKb())
                    .judgeMessage(result.getJudgeMsg())
                    .score(result.getScore())
                    .push();
        } finally {
            // 分别清理，避免一个产物清理失败阻止另一个。
            clearCompileResult(session.getCheckerCompileResult());
            clearCompileResult(session.getCompileResult());
        }
    }

    /** 编译选手代码，编译失败作为 CE 回写，系统异常继续向任务层传播。 */
    private boolean compileSolution(JudgeSession session) {
        JudgeExecutionRequest request = session.getRequest();

        AbstractLanguageAdapter adapter = languageAdapterRegistry.require(request.getLanguage());

        // 保存适配器
        session.setAdapter(adapter);

        if (adapter.isNeedCompile()) {
            session.status(JudgingConstant.COMPILE).push();
        }

        // 编译用户的代码
        CompileResult result = compiler.compile(request.getSourceCode(), adapter);

        // 保存编译结果到上下文中.
        session.setCompileResult(result);

        session.compilerMessage(result.getCompileMsg()).push();

        if (!SandBoxStatus.ACCEPTED.equals(result.getStatus())) {
            session.status(JudgingConstant.COMPILE_ERROR).push();
            return false;
        }
        return true;
    }

    /** 普通题运行选手程序，直接按现有文本规则比较答案。 */
    private JudgeResult normalJudge(JudgeSession session) {
        session.status(JudgingConstant.RUNNING).push();
        return runTestCases(session, exactOutputChecker);
    }

    /** 特殊题额外编译一次校验程序，各测试点复用该编译产物。 */
    private JudgeResult specialJudge(JudgeSession session) {
        CheckerConfig checkConfig = session.getRequest().getCheckerConfig();

        AbstractLanguageAdapter adapter = languageAdapterRegistry.require(checkConfig.getLanguage());
        session.setCheckerAdapter(adapter);

        // 这里的编译结果可以缓存起来, 在比赛时, 会产生很多复用, 减少不必要的开销.
        CompileResult result = compiler.compile(checkConfig.getSourceCode(), adapter);

        session.setCheckerCompileResult(result);
        if (!SandBoxStatus.ACCEPTED.equals(result.getStatus())) {
            throw new JudgeSystemException("校验程序编译失败: " + result.getCompileMsg());
        }

        session.status(JudgingConstant.RUNNING).push();

        return runTestCases(session, specialOutputChecker);
    }

    /** 交互题需要双向通信，当前版本明确拒绝，不能当普通测试点执行。 */
    private void interactiveJudge() {
        throw new JudgeSystemException("暂不支持交互题判题");
    }

    /** 共用测试点循环，只在答案校验步骤区分普通题和特殊题。 */
    private JudgeResult runTestCases(JudgeSession session, OutputChecker outputChecker) {
        JudgeResult judgeResult = new JudgeResult();

        JudgeExecutionRequest request = session.getRequest();

        // 测试相关信息
        TestDataRef testDataRef = request.getTestDataRef();

        // 获取语言适配器
        AbstractLanguageAdapter adapter = session.getAdapter();

        int maxTime = 0;
        int maxMemory = 0;

        RunContext runContext = RunContext.builder()
                .activeFile(session.getCompileResult().getActiveFile())
                .memoryLimitKb(request.getMemoryLimitKb())
                .timeLimitMs(request.getTimeLimitMs())
                .build();

        // 每次测评独立读取测试点，退出循环时释放读取器。
        try (TestCaseReader reader = fileTestCaseProvider.open(testDataRef)) {
            for(int caseNo = 1; caseNo <= testDataRef.getTestCaseCount(); caseNo++){
                // 加载测试数据
                TestCase testCase = reader.read(caseNo);

                runContext.setInput(testCase.getInput());

                // 获取运行结果
                RunResult runResult = runner.run(adapter, runContext);

                String status = runResult.getStatus();
                Integer exitStatus = runResult.getExitStatus();

                // 沙箱故障属于系统异常，先于资源统计处理，避免缺少统计字段掩盖原始故障。
                if (SandBoxStatus.INTERNAL_ERROR.equals(status) || SandBoxStatus.FILE_ERROR.equals(status)) {
                    throw new SystemErrorException("沙箱运行发生系统异常: " + status);
                }

                maxTime = Math.max(maxTime, (int)((runResult.getRunTimeNan() + 999_999L) / 1_000_000));
                maxMemory = Math.max(maxMemory, (int)((runResult.getMemoryByte() + 1023L) / 1024));

                // 运行时异常
                if (SandBoxStatus.SIGNALLED.equals(status) || SandBoxStatus.NONZERO_EXIT_STATUS.equals(status)) {
                    judgeResult.setStatus(JudgingConstant.RUNTIME_ERROR);
                    judgeResult.setJudgeMsg(status);
                }
                // 超时
                else if (SandBoxStatus.TIME_LIMIT_EXCEEDED.equals(status)) {
                    judgeResult.setStatus(JudgingConstant.TIME_LIMIT_EXCEEDED);
                    judgeResult.setJudgeMsg(status);
                }
                // 内存超限
                else if (SandBoxStatus.MEMORY_LIMIT_EXCEEDED.equals(status)) {
                    judgeResult.setStatus(JudgingConstant.MEMORY_LIMIT_EXCEEDED);
                    judgeResult.setJudgeMsg(status);
                }
                // 其他错误
                else if (!SandBoxStatus.ACCEPTED.equals(status) || !Integer.valueOf(0).equals(exitStatus)) {
                    judgeResult.setStatus(JudgingConstant.RUNTIME_ERROR);
                    judgeResult.setJudgeMsg(status);
                }
                // 正常
                else {
                    // 答案检测
                    CheckResult check = outputChecker.check(
                            OutputCheckContext.builder()
                                .input(testCase.getInput())
                                .expectedOutput(testCase.getExpectedOutput())
                                .actualOutput(runResult.getStdout())
                                .checkerAdapter(session.getCheckerAdapter())
                                .checkerCompileResult(session.getCheckerCompileResult())
                                .timeLimitMs(request.getTimeLimitMs())
                                .memoryLimitKb(request.getMemoryLimitKb())
                                .build()
                    );

                    if(check.isMatch()){
                        // 表示这个样例点通过
                        judgeResult.setStatus(JudgingConstant.ACCEPTED);
                    } else {
                        judgeResult.setStatus(JudgingConstant.WRONG_ANSWER);
                        judgeResult.setJudgeMsg(check.getMsg());
                    }

                }

                // 有一个不通过, 就结束, 不执行后面的测试样例.
                if(!JudgingConstant.ACCEPTED.equals(judgeResult.getStatus())){
                    break ;
                }
            }
        } catch (Exception e) {
            throw new SystemErrorException("系统异常: " + e.getMessage(), e);
        }

        judgeResult.setTimeMs(maxTime);
        judgeResult.setMemoryKb(maxMemory);
        judgeResult.setScore(0);

        return judgeResult;
    }

    /** 清理失败只记录日志，不覆盖已经得到的判题结果或原始异常。 */
    private void clearCompileResult(CompileResult result) {
        if (result == null) {
            return;
        }
        try {
            compiler.deleteCacheCompileFile(result);
        } catch (Exception exception) {
            log.warn("清理本次编译产物失败", exception);
        }
    }
}
