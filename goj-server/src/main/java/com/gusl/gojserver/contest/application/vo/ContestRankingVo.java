package com.gusl.gojserver.contest.application.vo;

import com.gusl.common.common.PageResult;
import com.gusl.common.pojo.entity.ContestProblem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import com.gusl.gojserver.contest.application.model.UserRankRow;

import java.util.List;

@Data
public class ContestRankingVo {

    @Schema(description = "比赛id")
    private Long contestId;

    @Schema(description = "比赛状态")
    private String contestStatus;

    @Schema(description = "比赛题目集合")
    private List<ContestProblem> contestProblems;

    @Schema(description = "每个用户比赛状况")
    private PageResult<UserRankRow> contestRankingRows;

}
