package com.gusl.gojjudge.judging.domain.checker;


import com.gusl.common.constant.CheckType;
import com.gusl.gojjudge.judging.domain.model.CheckResult;

/**
 * 答案不唯一的检测器
 */
public class SpecialJudgeOutputChecker implements OutputChecker {

    @Override
    public CheckType getCheckType() {
        return CheckType.SPECIAL;
    }

    @Override
    public CheckResult check(String input, String exceptedOutput, String actualOutput) {

        return null;
    }
}
