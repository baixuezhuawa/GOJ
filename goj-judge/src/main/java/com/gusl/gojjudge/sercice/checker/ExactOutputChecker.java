package com.gusl.gojjudge.sercice.checker;

import com.gusl.common.constant.CheckType;
import com.gusl.common.utils.StringUtils;
import com.gusl.gojjudge.exception.JudgeSystemException;
import com.gusl.gojjudge.pojo.entity.CheckResult;
import org.springframework.stereotype.Component;

/**
 * 标准输出检测器
 */
@Component
public class ExactOutputChecker implements OutputChecker {

    @Override
    public CheckType getCheckType() {
        return CheckType.TEXT;
    }

    @Override
    public CheckResult check(String input, String exceptedOutput, String actualOutput) {
        if(StringUtils.isEmpty(exceptedOutput)){
            throw new JudgeSystemException("标准输出内容为空");
        }
        String normalizedExcepted = normalize(exceptedOutput);
        String normalizedActual = normalize(actualOutput);

        if(normalizedExcepted.equals(normalizedActual)){
            return CheckResult.accepted();
        }

        return CheckResult.failed("输出与标准答案不一致");
    }

    private String normalize(String text){
        return text.replace("\r\n", "\n").stripTrailing();
    }

}
