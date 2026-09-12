package com.gusl.gojjudge.sercice.engine.strategy;

import com.gusl.common.constant.ExecutionMode;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.constant.SandBoxStatus;
import com.gusl.gojjudge.adapter.AbstractLanguageAdapter;
import com.gusl.gojjudge.exception.SystemErrorException;
import com.gusl.gojjudge.pojo.entity.*;
import com.gusl.gojjudge.sercice.engine.JudgeStrategy;
import com.gusl.gojjudge.sercice.checker.OutputChecker;
import com.gusl.gojjudge.sercice.testcase.TestCaseReader;
import com.gusl.gojjudge.sercice.testcase.LocalFileTestCaseProvider;
import com.gusl.gojjudge.sercice.checker.OutputCheckerRegistry;
import com.gusl.gojjudge.sercice.runner.Runner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 普通测评策略, 样例点
 */
@Component
@RequiredArgsConstructor
public class NormalJudgeStrategy implements JudgeStrategy {

    private final Runner runner;

    private final OutputCheckerRegistry outputCheckerRegistry;

    private final LocalFileTestCaseProvider fileTestCaseProvider;

    /**
     * 逐个测试点匹配
     * @return 判题策略
     */
    @Override
    public ExecutionMode executionMode() {
        return ExecutionMode.BATCH_TEST_NODE;
    }

    /**
     * 执行测评普通测评, 并获取测评结果.
     * @param context 测评上下文
     * @return 测评结果
     */
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

            for(int caseNo = 1; caseNo <= testDataRef.getTestCaseCount(); caseNo++){
                // 加载测试数据
                TestCase testCase = reader.read(caseNo);

                RunContext runContext = RunContext.builder()
                        .input(testCase.getInput())
                        .memoryLimitKb(request.getMemoryLimitKb())
                        .timeLimitMs(request.getTimeLimitMs())
                        .build();

                // 获取运行结果
                RunResult runResult = runner.run(adapter, runContext);

                maxTime = Math.max(maxTime, (int)(runResult.getRunTimeNan() + 999_999) / 1_000_000);
                maxMemory = Math.max(maxMemory, (int)(runResult.getMemoryByte() + 1023) / 1024);

                String status = runResult.getStatus();
                Integer exitStatus = runResult.getExitStatus();

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
                            testCase.getInput(),
                            testCase.getExpectedOutput(),
                            runResult.getStdout()
                    );

                    if(check.isMatch()){
                        // 表示这个样例点通过
                        judgeResult.setStatus(JudgingConstant.ACCEPTED);
                    } else {
                        judgeResult.setStatus(JudgingConstant.WRONG_ANSWER);
                    }

                }

                // 有一个不通过, 就结束, 不执行后面的测试样例.
                if(!JudgingConstant.ACCEPTED.equals(judgeResult.getStatus())){
                    break ;
                }
            }
        } catch (Exception e) {
            throw new SystemErrorException("系统异常: " + e.getMessage());
        }

        judgeResult.setTimeMs(maxTime);
        judgeResult.setMemoryKb(maxMemory);
        judgeResult.setScore(0);

        return judgeResult;
    }
}
