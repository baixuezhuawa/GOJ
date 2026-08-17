package com.gusl.gojserver.pojo.dto;

import com.gusl.common.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Schema(name = "分页查询", description = "问题分页查询, 条件分页查询")
@Data
public class ProblemPageListDto extends PageQuery {

    @Schema(description = "题目名/题号")
    private String keyword;

    @Schema(description = "难度最小值")
    private Integer difficultyMin;

    @Schema(description = "难度最大值")
    private Integer difficultyMax;

    @Schema(description = "标签")
    private List<Long> tagId;

    @Schema(description = "尝试, 未尝试, 通过")
    private String solveStatus;
}
