package com.gusl.gojserver.submission.application;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.common.common.PageResult;
import com.gusl.gojserver.submission.application.dto.Submission2JudgeDto;
import com.gusl.gojserver.submission.application.dto.SubmissionSearchDto;
import com.gusl.gojserver.account.infrastructure.security.LoginUser;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojserver.submission.application.vo.SubmissionDetailVo;
import com.gusl.gojserver.submission.application.vo.SubmissionListVo;
import com.gusl.gojserver.submission.application.vo.SubmissionVo;
import com.gusl.gojserver.submission.application.model.SubmissionRequestIdentity;

public interface SubmissionService extends IService<Submission> {

    /**
     * 将用户的提交, 提交到测评机
     * @param submission2JudgeDto 提交信息
     */
    SubmissionVo submitProblemToJudge(
            Submission2JudgeDto submission2JudgeDto,
            LoginUser loginUser,
            SubmissionRequestIdentity identity
    );

    /**
     * 获取测评详细信息
     * @param submissionId 提交 id
     */
    SubmissionDetailVo getSubmissionById(Long submissionId);

    /**
     * 获取我的提交列表
     */
    PageResult<SubmissionListVo> getMySubmissionList(LoginUser loginUser, SubmissionSearchDto condition);

    /**
     * 获取最近几次提交
     */
    PageResult<SubmissionListVo> getMyRecentSubmission(LoginUser loginUser);
}
