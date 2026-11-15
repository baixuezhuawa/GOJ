package com.gusl.gojjudge.judging.result;

import com.gusl.common.pojo.entity.ContestSubmission;
import com.gusl.common.pojo.entity.ProblemReviewSubmission;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojjudge.judging.model.JudgeOutcome;


/**
 * 普通提交结果写回服务，负责更新测评状态并触发终态派生数据更新器。
 */
public interface SubmissionResultService {

    /**
     * 写回普通提交的当前测评结果。
     *
     * @param submission 当前普通提交
     * @param outcome 当前测评结果
     */
    boolean updateSubmission(Submission submission, JudgeOutcome outcome);


    /**
     * 写回比赛提交的当前测评结果
     * @param contestSubmission 比赛提交
     * @param outcome 当前测评结果
     */
    boolean updateSubmission(ContestSubmission contestSubmission, JudgeOutcome outcome);


    /**
     * 写回验题提交的当前测评结果.
     * @param reviewSubmission 验题提交
     * @param cur 当前测评结果.
     */
    boolean updateSubmission(ProblemReviewSubmission reviewSubmission, JudgeOutcome cur);
    /** 重试耗尽后，按提交类型写入系统错误终态。 */
    void markSystemError(String taskType, Long businessId, String errorMessage);
}
