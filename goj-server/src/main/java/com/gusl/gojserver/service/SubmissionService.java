package com.gusl.gojserver.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.gojserver.pojo.dto.SubmissionDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojserver.pojo.vo.SubmissionVo;

public interface SubmissionService extends IService<Submission> {

    /**
     * 将用户的提交, 提交到测评机
     * @param submissionDto 提交信息
     */
    Long submitProblemToJudge(SubmissionDto submissionDto, LoginUser loginUser);

    SubmissionVo getSubmissionById(Long submissionId);
}
