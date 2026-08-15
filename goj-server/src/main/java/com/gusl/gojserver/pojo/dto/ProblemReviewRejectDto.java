package com.gusl.gojserver.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理员驳回题目请求。
 */
@Schema(name = "题目审核驳回请求")
@Data
public class ProblemReviewRejectDto {

    @Schema(description = "审核未通过原因", example = "测试数据缺少边界用例")
    private String remark;
}
