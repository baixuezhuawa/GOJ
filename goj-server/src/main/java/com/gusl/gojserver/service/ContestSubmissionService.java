package com.gusl.gojserver.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.common.common.PageQuery;
import com.gusl.common.common.PageResult;
import com.gusl.common.pojo.entity.ContestSubmission;
import com.gusl.gojserver.pojo.dto.ContestSubmission2JudgeDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.vo.ContestSubmissionDetailVo;
import com.gusl.gojserver.pojo.vo.ContestSubmissionListVo;
import com.gusl.gojserver.pojo.vo.SubmissionVo;

public interface ContestSubmissionService extends IService<ContestSubmission> {

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
