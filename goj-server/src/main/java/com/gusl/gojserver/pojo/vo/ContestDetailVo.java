package com.gusl.gojserver.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Data
public class ContestDetailVo {

    @Schema(description = "比赛id")
    private Long contestId;

    @Schema(description = "比赛名称")
    private String title;

    @Schema(description = "问题列表")
    private List<ContestProblemListVo> problems;

    @Schema(description = "距离比赛结束的剩余秒数")
    private Long remainingSeconds;

    @Schema(description = "比赛描述")
    private String description;

    @Schema(description = "比赛状态")
    private String status;
}
