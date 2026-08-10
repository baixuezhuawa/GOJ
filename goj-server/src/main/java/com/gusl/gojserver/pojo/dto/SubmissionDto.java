package com.gusl.gojserver.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(name = "提交Dto")
@Data
public class SubmissionDto {

    @Schema(description = "题目id")
    private Long problemId;

    @Schema(description = "代码语言")
    private String language;

    @Schema(description = "源代码")
    private String sourceCode;

    @Schema(description = "比赛id")
    private Long contestId;

}
