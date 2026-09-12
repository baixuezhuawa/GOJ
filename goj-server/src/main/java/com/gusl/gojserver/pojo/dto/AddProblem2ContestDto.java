package com.gusl.gojserver.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddProblem2ContestDto {

    @NotNull(message = "比赛ID不能为空")
    @Schema(description = "比赛id")
    private Long contestId;

    @NotNull(message = "题目ID不能为空")
    @Schema(description = "问题id")
    private Long problemId;

    @NotBlank(message = "展示名称不能为空")
    @Schema(description = "展示名称")
    private String displayName;

    @NotBlank(message = "题目展示编号不能为空")
    @Schema(description = "展示编号")
    private String displayCode;

    @NotNull(message = "题目排序号不能为空")
    @Schema(description = "排序, 越小越前")
    private Integer sortOrder;

}
