package com.gusl.gojjudge.judging.domain.checker;

import com.gusl.common.constant.CheckType;
import com.gusl.gojjudge.judging.domain.model.CheckResult;
import com.gusl.gojjudge.judging.domain.model.OutputCheckContext;

/** 输出校验接口，按校验类型判断当前测试点是否通过。 */
public interface OutputChecker {

    CheckType getCheckType();

    /** 根据当前测试点与校验程序上下文判断输出是否匹配。 */
    CheckResult check(OutputCheckContext context);

}
