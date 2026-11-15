package com.gusl.gojserver.problem.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理员验题代码请求。
 */
@Schema(name = "管理员验题请求")
@Data
public class ProblemReviewJudgeDto {

    @Schema(description = "测试数据版本")
    private Integer version;

    @Schema(description = "语言编码", example = "java11")
    private String language;

    @Schema(description = "验题代码")
    private String sourceCode;
}
