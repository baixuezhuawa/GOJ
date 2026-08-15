package com.gusl.gojserver.service;

import com.gusl.gojserver.pojo.vo.AdminProblemReviewDetailVo;
import com.gusl.gojserver.pojo.vo.AdminProblemReviewListVo;

import java.util.List;

/**
 * 管理员题目审核服务。
 */
public interface ProblemReviewService {

    /**
     * 分页获取待审核题目。
     *
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @return 待审核题目列表
     */
    List<AdminProblemReviewListVo> getPendingReviews(Integer page, Integer size);

    /**
     * 获取待审核题目详情和测试数据摘要。
     *
     * @param problemId 题目 id
     * @return 审核详情
     */
    AdminProblemReviewDetailVo getPendingReviewDetail(Long problemId);

    /**
     * 审核通过题目，并将测试数据发布到正式目录。
     *
     * @param problemId 题目 id
     */
    void approve(Long problemId);

    /**
     * 驳回题目并保存审核意见。
     *
     * @param problemId 题目 id
     * @param remark 驳回原因
     */
    void reject(Long problemId, String remark);
}
