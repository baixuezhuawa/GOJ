package com.gusl.gojserver.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员验题提交状态响应。
 */
@Schema(name = "管理员验题提交状态")
@Data
public class ProblemReviewSubmissionVo {

    @Schema(description = "验题提交 id")
    private Long id;

    @Schema(description = "题目 id")
    private Long problemId;

    @Schema(description = "测试数据集 id")
    private Long problemTestDataId;

    @Schema(description = "管理员 id")
    private Long reviewerId;

    @Schema(description = "语言编码")
    private String language;

    @Schema(description = "验题代码")
    private String sourceCode;

    @Schema(description = "测评状态")
    private String status;

    @Schema(description = "最大运行耗时，单位为毫秒")
    private Integer timeMs;

    @Schema(description = "最大运行内存，单位为 KB")
    private Integer memoryKb;

    @Schema(description = "编译信息")
    private String compilerMsg;

    @Schema(description = "测评信息")
    private String judgeMsg;

    @Schema(description = "提交时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submissionTime;
}
