package com.gusl.gojserver.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "比赛草稿")
@Data
public class ContestDto {

    @NotBlank(message = "比赛名称不能为空")
    @Schema(description = "比赛名称")
    private String title;

    @Size(max = 2000, message = "比赛描述不能超过2000个字符")
    @Schema(description = "比赛描述")
    private String description;

    @NotNull(message = "报名开始时间不能为空")
    @Schema(description = "报名开始时间")
    private LocalDateTime registerStartTime;

    @NotNull(message = "报名结束时间不能为空")
    @Schema(description = "报名结束时间")
    private LocalDateTime registerEndTime;

    @NotNull(message = "比赛开始时间不能为空")
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @NotNull(message = "比赛结束时间不能为空")
    @Schema(description = "结束时间")
    private LocalDateTime endTime;

}
