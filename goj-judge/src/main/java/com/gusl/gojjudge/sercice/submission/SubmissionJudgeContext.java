package com.gusl.gojjudge.sercice.submission;

import com.gusl.common.pojo.entity.JudgeTaskMessage;
import com.gusl.gojjudge.pojo.entity.JudgeExecutionRequest;
import com.gusl.gojjudge.pojo.entity.JudgeOutcome;

/** 一次提交测评所需的业务上下文。 */
public interface SubmissionJudgeContext {

    /** 返回本次判题请求。 */
    JudgeExecutionRequest getExecutionRequest();

    /** 写回一次测评状态。 */
    boolean write(JudgeTaskMessage task, JudgeOutcome outcome);

    /** 判断对应业务记录是否已经结束。 */
    boolean isFinished();
}
