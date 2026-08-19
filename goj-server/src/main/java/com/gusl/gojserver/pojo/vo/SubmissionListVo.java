package com.gusl.gojserver.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubmissionListVo {

    @Schema(description = "提交id")
    private Long id;

    @Schema(description = "作者")
    private String username;

    @Schema(description = "执行耗时")
    private Integer timeMs;

    @Schema(description = "内存消耗")
    private Integer memoryKb;

    @Schema(description = "测评状态")
    private String status;

    @Schema(description = "提交时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submissionTime;
}
