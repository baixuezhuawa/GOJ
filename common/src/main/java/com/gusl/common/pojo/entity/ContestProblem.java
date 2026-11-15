package com.gusl.common.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

@Data
public class ContestProblem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long contestId;

    private Long problemId;

    private String displayName;

    private String displayCode;

    private Long testDataId;

    private String releaseStatus;

    private Integer sortOrder;
}
