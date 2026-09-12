package com.gusl.gojserver.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProfileStatisticsVo {

    @Schema(description = "已解决问题")
    private Long solveNumber;

    @Schema(description = "尝试过但未解决的问题")
    private Long unSolveNumber;

    @Schema(description = "提交总数")
    private Long submitCount;

    @Schema(description = "近一个月解决问题的数量")
    private Long solveLastMonth;

    @Schema(description = "近一年解决问题的数量")
    private Long solveLastYear;

    @Schema(description = "当前连续做题天数")
    private Integer longestConsecutiveDays;
}
