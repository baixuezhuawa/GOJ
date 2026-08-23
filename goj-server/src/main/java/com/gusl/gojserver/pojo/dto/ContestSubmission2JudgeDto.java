package com.gusl.gojserver.pojo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ContestSubmission2JudgeDto {

    @NotBlank(message = "提价语言不能为空")
    private String language;

    @NotBlank(message = "提交代码不能为空")
    @Max(value = 4000, message = "最大长度超出限制")
    private String sourceCode;

}
