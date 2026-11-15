package com.gusl.gojjudge.judging.checker;


import com.gusl.common.constant.CheckType;
import com.gusl.common.constant.SandBoxStatus;
import com.gusl.gojjudge.judging.exception.JudgeSystemException;
import com.gusl.gojjudge.judging.model.CheckResult;
import com.gusl.gojjudge.judging.model.OutputCheckContext;
import com.gusl.gojjudge.judging.runner.RunContext;
import com.gusl.gojjudge.judging.runner.RunResult;
import com.gusl.gojjudge.judging.runner.Runner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 答案不唯一校验器，将选手输出交给校验程序，再比对校验程序输出。
 */
@Component
@RequiredArgsConstructor
public class SpecialOutputChecker implements OutputChecker {

    private final Runner runner;

    private final ExactOutputChecker exactOutputChecker;

    /** 返回答案不唯一的校验类型。 */
    @Override
    public CheckType getCheckType() {
        return CheckType.SPECIAL;
    }

    /** 运行校验程序，将其标准输出与测试点预期输出比对。 */
    @Override
    public CheckResult check(OutputCheckContext context) {
        // 选手输出作为校验程序的标准输入，复用已有沙箱运行模块。
        RunContext runContext = RunContext.builder()
                .input(context.getActualOutput())
                .activeFile(context.getCheckerCompileResult().getActiveFile())
                .timeLimitMs(context.getTimeLimitMs())
                .memoryLimitKb(context.getMemoryLimitKb())
                .build();
        RunResult result = runner.run(context.getCheckerAdapter(), runContext);

        // 校验程序自身失败属于系统故障，不能记作选手的 WA 或 RE。
        if (!SandBoxStatus.ACCEPTED.equals(result.getStatus())
                || !Integer.valueOf(0).equals(result.getExitStatus())) {
            throw new JudgeSystemException("校验程序运行失败: " + result.getStatus()
                    + "，退出码: " + result.getExitStatus() + "，错误输出: " + result.getStderr());
        }

        // 校验程序的输出仍按现有文本规则与 output.txt 比对。
        return exactOutputChecker.check(OutputCheckContext.builder()
                .expectedOutput(context.getExpectedOutput())
                .actualOutput(result.getStdout())
                .build());
    }
}
