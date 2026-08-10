package com.gusl.gojserver.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(name = "分页查询", description = "问题分页查询, 条件分页查询") @Data
public class ProblemPageListDto {

    @Schema(description = "页码")
    private Integer page;

    @Schema(description = "每页大小")
    private Integer size;

    @Schema(description = "题目名/题号")
    private String keyword;

    @Schema(description = "难度最小值")
    private Integer difficultyMin;

    @Schema(description = "难度最大值")
    private Integer difficultyMax;

    @Schema(description = "标签")
    private List<Long> tagId;

    @Schema(description = "")
    private boolean unsolved;
}
