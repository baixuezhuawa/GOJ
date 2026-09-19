package com.gusl.gojserver.shared.infrastructure.config.properties.entity;

import lombok.Data;

@Data
public class FileProperties {

    private Integer maxEntry;

    private Long maxTotalBytes;
}
