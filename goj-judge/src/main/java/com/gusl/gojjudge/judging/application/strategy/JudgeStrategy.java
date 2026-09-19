package com.gusl.gojjudge.judging.application.strategy;

import com.gusl.common.constant.ExecutionMode;
import com.gusl.gojjudge.judging.application.engine.JudgeContext;
import com.gusl.gojjudge.judging.domain.model.JudgeResult;

/**
 * 执行策略
 */
public interface JudgeStrategy {

    /**
     * @return 该执行策略支持的测评方式
     */
    ExecutionMode executionMode();


    /**
     * 具体判题流程
     * @param context 测评上下文
     * @return 判题结果
     */
    JudgeResult judge(JudgeContext context);


}
