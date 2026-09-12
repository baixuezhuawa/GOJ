package com.gusl.gojserver.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JudgeTaskListVo {

    @Schema(description = "任务id")
    private Long taskId;

    @Schema(description = "提交id")
    private Long submissionId;

    @Schema(description = "任务类型")
    private String taskType;

    @Schema(description = "任务版本")
    private Integer taskVersion;

    @Schema(description = "任务状态")
    private String status;

    @Schema(description = "实际执行次数")
    private Integer attemptCount;

    @Schema(description = "最近一次错误信息")
    private String lastError;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

}
