package com.gusl.gojserver.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "问题列表")
public class ContestProblemListVo {

    @Schema(description = "题目展示名称")
    private String displayName;

    @Schema(description = "题目展示编号")
    private String displayCode;
}
