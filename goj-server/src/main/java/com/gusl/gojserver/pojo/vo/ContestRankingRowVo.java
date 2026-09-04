package com.gusl.gojserver.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class ContestRankingRowVo {

    @Schema(description = "排名")
    private Integer rank;

    @Schema(description = "用户id")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "解决问题数量")
    private Integer solveCount;

    @Schema(description = "罚时")
    private Integer penaltyMinutes;

    @Schema(description = "每个问题解决状况")
    private List<ProblemResults> problemResults;

}
