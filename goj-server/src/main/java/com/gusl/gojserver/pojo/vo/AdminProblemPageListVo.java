package com.gusl.gojserver.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员题目列表条目，在普通题目列表信息之外显示题目发布状态。
 */
@EqualsAndHashCode(callSuper = true)
@Schema(name = "管理员题目列表条目", description = "管理员查看的题目基本信息和题目状态")
@Data
public class AdminProblemPageListVo extends ProblemPageListVo {

    /** 题目状态。 */
    @Schema(description = "题目状态：0 草稿，1 已发布，2 已停用，3 待审核，4 已退回，5 准备中")
    private Integer problemStatus;
}
