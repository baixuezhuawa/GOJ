package com.gusl.gojjudge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gusl.common.pojo.entity.ProblemReviewSubmission;
import org.apache.ibatis.annotations.Mapper;

/**
 * Judge Worker 管理员验题提交 Mapper。
 */
@Mapper
public interface ProblemReviewSubmissionMapper extends BaseMapper<ProblemReviewSubmission> {
}
