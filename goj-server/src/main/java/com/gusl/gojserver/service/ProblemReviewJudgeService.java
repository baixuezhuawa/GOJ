package com.gusl.gojserver.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.common.pojo.entity.ProblemReviewSubmission;
import com.gusl.gojserver.pojo.dto.ProblemReviewJudgeDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.vo.ProblemReviewSubmissionVo;

/**
 * 管理员验题提交服务。
 */
public interface ProblemReviewJudgeService extends IService<ProblemReviewSubmission> {

    /**
     * 创建管理员验题任务。
     *
     * @param problemId 待审核题目 id
     * @param dto 语言和验题代码
     * @param loginUser 当前管理员
     * @return 验题提交 id
     */
    Long submit(Long problemId, ProblemReviewJudgeDto dto, LoginUser loginUser);

    /**
     * 获取管理员验题任务状态。
     *
     * @param reviewSubmissionId 验题提交 id
     * @return 验题状态
     */
    ProblemReviewSubmissionVo getReviewSubmission(Long reviewSubmissionId);
}
