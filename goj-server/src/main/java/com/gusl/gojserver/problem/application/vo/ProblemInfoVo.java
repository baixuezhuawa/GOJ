package com.gusl.gojserver.problem.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 公开题目详情，只声明允许对普通用户展示的字段。 */
@Schema(name = "问题详细信息vo")
@Data
public class ProblemInfoVo {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 题目标题。 */
    @Schema(description = "题目名称")
    private String problemName;

    /** 题目类型：0 普通，1 答案不唯一，2 交互。 */
    @Schema(description = "题目类型：0 普通，1 答案不唯一，2 交互")
    private Integer problemType;

    /** 时间限制，单位为毫秒。 */
    @Schema(description = "时间限制/ms")
    private Integer timeLimit;

    /** 内存限制，单位为 KB。 */
    @Schema(description = "空间限制/KB")
    private Integer memoryLimit;

    /** 题面描述。 */
    @Schema(description = "题面描述")
    private String description;

    /** 输入格式描述。 */
    @Schema(description = "输入描述")
    private String inputDescription;

    /** 输出格式描述。 */
    @Schema(description = "输出描述")
    private String outPutDescription;

    /** 公开输入样例。 */
    @Schema(description = "输入样例")
    private String inputExample;

    /** 公开输出样例。 */
    @Schema(description = "输出样例")
    private String outPutExample;

    /** 样例说明，可为空。 */
    @Schema(description = "样例说明")
    private String exampleNote;

    /** 题目难度分。 */
    @Schema(description = "难度分")
    private Integer difficulty;

    /** 出题人用户 id。 */
    @Schema(description = "出题人用户 id")
    private Long authorId;

    @Schema(description = "题目状态：0 草稿，1 已发布，2 已停用")
    private Integer status;

    @Schema(description = "作者名称")
    private String authorName;

    @Schema(description = "题目标签")
    private List<String> tags;
}
