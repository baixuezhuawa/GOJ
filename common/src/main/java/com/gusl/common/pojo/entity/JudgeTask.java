package com.gusl.common.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.gusl.common.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 测评任务实体，记录普通提交和管理员验题任务的调度、重试及执行状态。
 */
@Schema(description = "测评任务实体")
@TableName("judge_task")
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class JudgeTask extends BaseEntity {

    /** 测评任务 id。 */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "测评任务 id")
    private Long id;

    /** 任务类型，例如 SUBMISSION、PROBLEM_REVIEW。 */
    @Schema(description = "任务类型")
    private String taskType;

    /** 对应的提交或验题提交记录 id。 */
    @Schema(description = "业务记录 id")
    private Long businessId;

    /** 同一业务记录重新测评时递增的任务版本。 */
    @Schema(description = "任务版本")
    private Integer taskVersion;

    /** 任务状态，例如 PENDING、PROCESSING、RETRY_WAIT、SUCCEEDED、DEAD。 */
    @Schema(description = "任务状态")
    private String status;

    /** 任务已经实际执行的次数。 */
    @Schema(description = "实际执行次数")
    private Integer attemptCount;

    /** 允许任务执行的最大次数。 */
    @Schema(description = "最大执行次数")
    private Integer maxAttempts;

    /** 任务下次允许重试的时间。 */
    @Schema(description = "下次重试时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nextRetryTime;

    /** 当前领取任务的 Worker 标识。 */
    @Schema(description = "当前 Worker 标识")
    private String leaseOwner;

    /** 当前任务处理租约的过期时间。 */
    @Schema(description = "租约过期时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime leaseExpireTime;

    /** 最近一次将任务发送到消息队列的时间。 */
    @Schema(description = "最近调度时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastDispatchTime;

    /** 最近一次执行失败的简要错误信息。 */
    @Schema(description = "最近一次错误信息")
    private String lastError;
}