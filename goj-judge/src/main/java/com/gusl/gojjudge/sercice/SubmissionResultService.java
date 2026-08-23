package com.gusl.gojjudge.sercice;

import com.gusl.common.pojo.entity.ContestSubmission;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojjudge.pojo.entity.JudgeOutcome;


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
    void updateSubmission(Submission submission, JudgeOutcome outcome);


    /**
     * 写回比赛提交的当前测评结果
     * @param contestSubmission 比赛提交
     * @param outcome 当前测评结果
     */
    void updateSubmission(ContestSubmission contestSubmission, JudgeOutcome outcome);
}
