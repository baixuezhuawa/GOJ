package com.gusl.gojjudge.judging.application.handler;

import com.gusl.common.pojo.entity.JudgeTaskMessage;

public interface SubmissionJudgeHandler {

    String handlerTaskType();

    SubmissionJudgeContext prepare(JudgeTaskMessage message);

}
