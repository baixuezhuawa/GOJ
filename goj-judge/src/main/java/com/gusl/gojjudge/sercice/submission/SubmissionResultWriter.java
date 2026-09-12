package com.gusl.gojjudge.sercice.submission;


import com.gusl.common.pojo.entity.JudgeTaskMessage;
import com.gusl.gojjudge.pojo.entity.JudgeOutcome;

public interface SubmissionResultWriter {

    /**
     * 写回本次测评信息。
     *
     * @return 本次写回被接受并成功提交时返回 true；
     *         任务失效、写回被拒绝时返回 false
     */
    boolean write(JudgeTaskMessage task, JudgeOutcome outcome);

}
