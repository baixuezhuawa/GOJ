package com.gusl.common.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gusl.common.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 题目实体，保存题面、限制、难度和发布状态。
 *
 * <p>公开样例保留在题目表中；隐藏测试数据放在 problem_case 表中，
 * 避免普通题目详情接口直接返回测试数据。</p>
 */
@Tag(name = "题目实体")
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("problem")
public class Problem extends BaseEntity {

    /** 主键 id。 */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键 id")
    private Long id;

    /** 题目标题。 */
    @Schema(description = "题目名称")
    private String problemName;

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
    @TableField("output_description")
    @Schema(description = "输出描述")
    private String outPutDescription;

    /** 公开输入样例。 */
    @Schema(description = "输入样例")
    private String inputExample;

    /** 公开输出样例。 */
    @TableField("output_example")
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

    /** 题目状态：0 草稿，1 已发布，2 已停用。 */
    @Schema(description = "题目状态：0 草稿，1 已发布，2 已停用")
    private Integer status;
}
