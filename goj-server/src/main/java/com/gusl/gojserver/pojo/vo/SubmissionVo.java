package com.gusl.gojserver.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Schema(name = "测评状态")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubmissionVo {

    @Schema(description = "提交id")
    private Long id;

    @Schema(description = "作者")
    private String username;

    @Schema(description = "语言")
    private String language;

    @Schema(description = "源代码")
    private String sourceCode;

    @Schema(description = "测评状态")
    private String status;

    @Schema(description = "提交时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submissionTime;

    @Schema(description = "测评信息")
    private String judgeMsg;

    @Schema(description = "编译信息")
    private String compileMsg;
}
