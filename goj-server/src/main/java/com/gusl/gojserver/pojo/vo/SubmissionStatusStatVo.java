package com.gusl.gojserver.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提交终态统计，保存一种评测结果及其提交数量。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubmissionStatusStatVo {

    @Schema(description = "提交终态")
    private String status;

    @Schema(description = "该终态的提交数量")
    private Long submissionCount;
}