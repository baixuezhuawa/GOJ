package com.gusl.gojserver.contest.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ContestSubmission2JudgeDto {

    /** 本次逻辑提交的 UUID，重试时保持不变。 */
    private String requestId;

    @NotBlank(message = "提价语言不能为空")
    private String language;

    @NotBlank(message = "提交代码不能为空")
    private String sourceCode;

}
