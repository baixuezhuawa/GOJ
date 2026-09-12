package com.gusl.gojserver.pojo.vo;

import com.gusl.common.common.PageResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ContestRankingVo {

    @Schema(description = "比赛id")
    private Long contestId;

    @Schema(description = "比赛状态")
    private String contestStatus;

    @Schema(description = "每个用户比赛状况")
    private PageResult<ContestRankingRowVo> contestRankingRows;

}
