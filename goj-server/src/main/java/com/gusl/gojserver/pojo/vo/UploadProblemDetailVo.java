package com.gusl.gojserver.pojo.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;


@Data
public class UploadProblemDetailVo {

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


    /** 题目状态：0 草稿，1 已发布，2 已停用，3 待审核，4 已退回。 */
    @Schema(description = "题目状态：0 草稿，1 已发布，2 已停用，3 待审核，4 已退回, 5 准备中")
    private Integer status;


    /** 备注信息 */
    @Schema(description = "管理员评审备注信息")
    private String remark;

    /**
     * 管理员可以直接修改标签, 所以需要标签id
     */
    @Schema(description = "标签集合")
    private List<ProblemTagsVo> tags;


    @Schema(description = "测试数据集合")
    private List<ProblemTestDataListVo> testDataInfoList;

}
