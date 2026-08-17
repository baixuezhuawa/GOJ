package com.gusl.gojserver.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.common.common.PageResult;
import com.gusl.gojserver.pojo.dto.Submission2JudgeDto;
import com.gusl.gojserver.pojo.dto.SubmissionSearchDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojserver.pojo.vo.SubmissionDetailVo;
import com.gusl.gojserver.pojo.vo.SubmissionListVo;

import java.util.List;

public interface SubmissionService extends IService<Submission> {

    /**
     * 将用户的提交, 提交到测评机
     * @param submission2JudgeDto 提交信息
     */
    Long submitProblemToJudge(Submission2JudgeDto submission2JudgeDto, LoginUser loginUser);

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
