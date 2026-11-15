package com.gusl.gojserver.problem.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理员题目信息请求，创建和编辑共用。
 */
@Schema(name = "管理员题目信息")
@Data
public class ProblemSaveDto {

    /** 题目标题 */
    @Schema(description = "题目名称")
    private String problemName;

    /** 题目类型，创建时省略则为普通题，编辑时省略则保持原值。 */
    @Schema(description = "题目类型：0 普通，1 答案不唯一，2 交互")
    private Integer problemType;

    /** 时间限制 /ms */
    @Schema(description = "时间限制/ms")
    private Integer timeLimit;

    /** 内存限制 /kb */
    @Schema(description = "空间限制/KB")
    private Integer memoryLimit;

    /** 题面描述 */
    @Schema(description = "题面描述")
    private String description;

    /** 输入格式描述 */
    @Schema(description = "输入描述")
    private String inputDescription;

    /** 输出格式描述 */
    @Schema(description = "输出描述")
    private String outPutDescription;

    /** 公开输入样例 */
    @Schema(description = "输入样例")
    private String inputExample;

    /** 公开输出样例 */
    @Schema(description = "输出样例")
    private String outPutExample;

    /** 样例说明 */
    @Schema(description = "样例说明")
    private String exampleNote;

}
