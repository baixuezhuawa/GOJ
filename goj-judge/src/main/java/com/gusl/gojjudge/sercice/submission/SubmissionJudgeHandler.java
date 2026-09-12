package com.gusl.gojjudge.sercice.submission;

import com.gusl.common.pojo.entity.JudgeTaskMessage;

public interface SubmissionJudgeHandler {

    String handlerTaskType();

    SubmissionJudgeContext prepare(JudgeTaskMessage message);

}
