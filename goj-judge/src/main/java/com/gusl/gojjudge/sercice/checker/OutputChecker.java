package com.gusl.gojjudge.sercice.checker;

import com.gusl.common.constant.CheckType;
import com.gusl.gojjudge.pojo.entity.CheckResult;

public interface OutputChecker {

    CheckType getCheckType();

    CheckResult check(String input, String exceptedOutput, String actualOutput);

}
