package com.gusl.common.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.gusl.common.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@TableName("user_activity_day")
@Schema(description = "用户日期活跃实体")
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class UserActivityDay extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "日期活跃id")
    private Long id;

    @Schema(description = "用户id")
    private Long userId;

    @Schema(description = "活跃日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate activityDate;

    @Schema(description = "当天 Accepted 提交数量")
    private Integer acceptedCount;

    @Schema(description = "当天新 Accepted 提交数量")
    private Integer newSolvedProblemCount;

}
