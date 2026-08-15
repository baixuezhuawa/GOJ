package com.gusl.gojserver.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员审核题目详情。
 */
@EqualsAndHashCode(callSuper = true)
@Schema(name = "管理员审核题目详情")
@Data
public class AdminProblemReviewDetailVo extends ProblemInfoVo {

    @Schema(description = "作者用户名")
    private String username;

    @Schema(description = "待审核测试数据摘要")
    private ProblemTestDataReviewVo testData;
}
