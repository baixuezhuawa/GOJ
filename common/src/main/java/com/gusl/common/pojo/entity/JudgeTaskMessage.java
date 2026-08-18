package com.gusl.common.pojo.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "信息传输对象")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JudgeTaskMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "消息格式版本")
    private Integer schemaVersion;

    /** judgeTask的 id */
    @Schema(description = "对应judge_task.id")
    private Long taskId;

    /** 任务类型 */
    @Schema(description = "任务类型")
    private String taskType;

    /** 提交id */
    @Schema(description = "提交id")
    private Long businessId;

    /** 任务版本 */
    @Schema(description = "任务版本")
    private Integer taskVersion;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

}
