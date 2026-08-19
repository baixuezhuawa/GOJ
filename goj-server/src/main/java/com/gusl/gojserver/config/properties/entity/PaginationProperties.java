package com.gusl.gojserver.config.properties.entity;

import lombok.Data;

/**
 * 分页配置，负责定义系统统一的分页默认值和最大值。
 */
@Data
public class PaginationProperties {

    /** 默认页码 */
    private Long defaultPage = 1L;

    /** 默认每页数量 */
    private Long defaultSize = 20L;

    /** 单页最大数量 */
    private Long maxSize = 100L;
}