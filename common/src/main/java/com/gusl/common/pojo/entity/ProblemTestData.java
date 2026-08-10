package com.gusl.common.pojo.entity;

import com.gusl.common.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ProblemTestData extends BaseEntity {

    private Long id;

    private Long problemId;

    private Integer version;

    private Integer testNodeCount;

    private String archiveName;

    private String storagePath;

    private String archiveSha256;

    private String status;

    private Boolean active;
}
