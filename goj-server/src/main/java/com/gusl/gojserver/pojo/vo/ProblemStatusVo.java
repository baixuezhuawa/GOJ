package com.gusl.gojserver.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 题目状态选项，向前端提供数据库状态值和对应中文名称。
 */
@Data
@AllArgsConstructor
@Schema(name = "题目状态选项")
public class ProblemStatusVo {

    /** 数据库 problem.status 字段使用的整数状态值。 */
    @Schema(description = "数据库题目状态值")
    private Integer status;

    /** 状态对应的中文名称。 */
    @Schema(description = "题目状态名称")
    private String label;
}
