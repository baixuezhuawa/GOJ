package com.gusl.common.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分页查询参数
 */
@Data
public class PageQuery {

    @Schema(description = "页码, 从 1 开始")
    private Long page;

    @Schema(description = "每页数量")
    private Long size;
}
