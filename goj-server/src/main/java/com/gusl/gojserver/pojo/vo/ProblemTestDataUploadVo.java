package com.gusl.gojserver.pojo.vo;

import lombok.Data;

@Data
public class ProblemTestDataUploadVo {
    private Long testDataId;

    private Long problemId;

    private Integer version;

    private Integer testNodeCount;

    private String status;

    private String sha256;
}
