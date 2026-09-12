package com.gusl.gojserver.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "全部问题标签")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProblemTagsVo {

    @Schema(description = "标签id")
    private Long tagId;

    @Schema(description = "标签名称")
    private String tagName;
}
