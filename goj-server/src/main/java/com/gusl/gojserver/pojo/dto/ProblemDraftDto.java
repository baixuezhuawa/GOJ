package com.gusl.gojserver.pojo.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(name = "问题简单草稿")
@Data
public class ProblemDraftDto {

    /** 题目标题 */
    @Schema(description = "题目名称")
    private String problemName;

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
    @TableField("output_description")
    @Schema(description = "输出描述")
    private String outPutDescription;

    /** 公开输入样例 */
    @Schema(description = "输入样例")
    private String inputExample;

    /** 公开输出样例 */
    @TableField("output_example")
    @Schema(description = "输出样例")
    private String outPutExample;

    /** 样例说明 */
    @Schema(description = "样例说明")
    private String exampleNote;

}
