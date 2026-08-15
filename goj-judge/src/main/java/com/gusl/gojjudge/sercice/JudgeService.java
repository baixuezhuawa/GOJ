package com.gusl.gojjudge.sercice;

/**
 * Judge 测评服务接口。
 */
public interface JudgeService {

    /**
     * 执行普通用户提交的测评任务。
     *
     * @param submissionId 用户提交 id
     */
    void judgeSubmission(Long submissionId);

    /**
     * 执行管理员验题提交的测评任务。
     *
     * @param reviewSubmissionId 验题提交 id
     */
    void judgeProblemReview(Long reviewSubmissionId);
}
