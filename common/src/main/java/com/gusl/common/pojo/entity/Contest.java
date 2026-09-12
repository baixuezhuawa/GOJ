package com.gusl.common.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.gusl.common.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Schema(name = "比赛")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Contest extends BaseEntity {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 比赛名称 */
    @Schema(description = "比赛名称")
    private String title;

    @Schema(description = "比赛状态")
    private String status;

    /** 比赛描述 */
    @Schema(description = "比赛描述")
    private String description;

    /** 报名开始时间 */
    @Schema(description = "报名开始时间")
    private LocalDateTime registerStartTime;

    /** 报名结束时间 */
    @Schema(description = "报名结束时间")
    private LocalDateTime registerEndTime;

    /** 开始时间 */
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    /** 结束时间 */
    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}
