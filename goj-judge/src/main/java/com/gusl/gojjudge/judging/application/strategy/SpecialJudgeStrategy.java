package com.gusl.gojjudge.judging.application.strategy;

import com.gusl.common.constant.ExecutionMode;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.constant.SandBoxStatus;
import com.gusl.gojjudge.judging.application.engine.JudgeContext;
import com.gusl.gojjudge.judging.application.engine.JudgeExecutionRequest;
import com.gusl.gojjudge.judging.domain.checker.OutputChecker;
import com.gusl.gojjudge.judging.domain.exception.SystemErrorException;
import com.gusl.gojjudge.judging.domain.model.*;
import com.gusl.gojjudge.judging.infrastructure.checker.OutputCheckerRegistry;
import com.gusl.gojjudge.judging.infrastructure.language.AbstractLanguageAdapter;
import com.gusl.gojjudge.judging.infrastructure.runner.RunContext;
import com.gusl.gojjudge.judging.infrastructure.runner.RunResult;
import com.gusl.gojjudge.judging.infrastructure.runner.Runner;
import com.gusl.gojjudge.judging.infrastructure.testcase.LocalFileTestCaseProvider;
import com.gusl.gojjudge.judging.infrastructure.testcase.TestCaseReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpecialJudgeStrategy implements JudgeStrategy{

    private final Runner runner;

    private final OutputCheckerRegistry outputCheckerRegistry;

    private final LocalFileTestCaseProvider fileTestCaseProvider;


    @Override
    public ExecutionMode executionMode() {
        return ExecutionMode.SPECIAL;
    }

    @Override
    public JudgeResult judge(JudgeContext context) {

        JudgeResult judgeResult = new JudgeResult();

        JudgeExecutionRequest request = context.getRequest();

        // 测试相关信息
        TestDataRef testDataRef = request.getTestDataRef();

        // 获取语言适配器
        AbstractLanguageAdapter adapter = context.getAdapter();

        // 获取数据校验器.
        OutputChecker outputChecker = outputCheckerRegistry.require(request.getCheckerConfig().getCheckType());

        // 测试数据reader
        TestCaseReader reader = fileTestCaseProvider.open(testDataRef);

        int maxTime = 0;
        int maxMemory = 0;

        try {

            log.info("running");

            for(int caseNo = 1; caseNo <= testDataRef.getTestCaseCount(); caseNo++){
                // 加载测试数据
                TestCase testCase = reader.read(caseNo);

                RunContext runContext = RunContext.builder()
                        .input(testCase.getInput())
                        .activeFile(context.getCompileResult().getActiveFile())
                        .memoryLimitKb(request.getMemoryLimitKb())
                        .timeLimitMs(request.getTimeLimitMs())
                        .build();

                log.info("compileResult={}, activeFile={}",
                        context.getCompileResult(),
                        context.getCompileResult() == null
                                ? null
                                : context.getCompileResult().getActiveFile());

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
                    CheckResult check = outputChecker.check(OutputCheckContext.builder()
                            .input(testCase.getInput())
                            .expectedOutput(testCase.getExpectedOutput())
                            .actualOutput(runResult.getStdout())
                            .checkerAdapter(context.getCheckerAdapter())
                            .checkerCompileResult(context.getCheckerCompileResult())
                            .timeLimitMs(request.getTimeLimitMs())
                            .memoryLimitKb(request.getMemoryLimitKb())
                            .build());

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
}
