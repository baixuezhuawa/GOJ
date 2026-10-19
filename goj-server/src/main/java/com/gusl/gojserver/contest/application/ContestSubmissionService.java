package com.gusl.gojserver.contest.application;


import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.common.common.PageQuery;
import com.gusl.common.common.PageResult;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojserver.contest.application.dto.ContestSubmission2JudgeDto;
import com.gusl.gojserver.account.infrastructure.security.LoginUser;
import com.gusl.gojserver.contest.application.vo.ContestSubmissionDetailVo;
import com.gusl.gojserver.contest.application.vo.ContestSubmissionListVo;
import com.gusl.gojserver.submission.application.vo.SubmissionVo;

public interface ContestSubmissionService extends IService<Submission> {

    /**
     * 提交代码
     */
    SubmissionVo submitContestProblemToJudge(
            Long contestId,
            String problemCode,
            ContestSubmission2JudgeDto submission,
            LoginUser loginUser
    );

    /**
     * 获取赛时提交列表
     * @return ContestSubmissionListVo
     */
    PageResult<ContestSubmissionListVo> getMyContestSubmissionList(
            Long contestId,
            PageQuery query,
            LoginUser loginUser
    );

    /**
     * 获取赛时提价详情
     * @return ContestSubmissionDetailVo
     */
    ContestSubmissionDetailVo getContestSubmissionDetail(Long contestId, Long submissionId, LoginUser loginUser);
}
