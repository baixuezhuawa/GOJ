package com.gusl.gojserver.pojo.vo;

import com.gusl.common.pojo.entity.Problem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TotalProblemInfoVo extends Problem {

    @Schema(description = "作者名称")
    private String authorName;

    /**
     * 管理员可以直接修改标签, 所以需要标签id
     */
    @Schema(description = "标签集合")
    private List<ProblemTagsVo> tags;

    @Schema(description = "测试数据集合")
    private List<ProblemTestDataListVo> testDataInfoList;
}
