package com.gusl.gojjudge.judging.domain.checker;

import com.gusl.common.constant.CheckType;
import com.gusl.gojjudge.judging.domain.model.CheckResult;

public interface OutputChecker {

    CheckType getCheckType();

    CheckResult check(String input, String exceptedOutput, String actualOutput);

}
