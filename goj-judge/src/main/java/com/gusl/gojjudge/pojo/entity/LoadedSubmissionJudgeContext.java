package com.gusl.gojjudge.pojo.entity;

import com.gusl.common.pojo.entity.JudgeTaskMessage;
import com.gusl.gojjudge.sercice.submission.SubmissionJudgeContext;

import java.util.function.BiFunction;

/** 保存一次任务已加载材料的提交上下文。 */
public class LoadedSubmissionJudgeContext implements SubmissionJudgeContext {

    private final JudgeExecutionRequest request;
    private final BiFunction<JudgeTaskMessage, JudgeOutcome, Boolean> writer;
    private final boolean finished;

    public LoadedSubmissionJudgeContext(
            JudgeExecutionRequest request,
            BiFunction<JudgeTaskMessage, JudgeOutcome, Boolean> writer,
            boolean finished
    ) {
        this.request = request;
        this.writer = writer;
        this.finished = finished;
    }

    @Override
    public JudgeExecutionRequest getExecutionRequest() {
        return request;
    }

    @Override
    public boolean write(JudgeTaskMessage task, JudgeOutcome outcome) {
        return Boolean.TRUE.equals(writer.apply(task, outcome));
    }

    @Override
    public boolean isFinished() {
        return finished;
    }
}
