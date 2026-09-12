package com.gusl.gojserver.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户语言使用统计，展示一种语言的提交数量和通过数量。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LanguageStatVo {

    @Schema(description = "语言编码")
    private String language;

    @Schema(description = "语言显示名称")
    private String displayName;

    @Schema(description = "该语言的终态提交数量")
    private Long submissionCount;

    @Schema(description = "该语言的 Accepted 提交数量")
    private Long acceptedCount;
}
