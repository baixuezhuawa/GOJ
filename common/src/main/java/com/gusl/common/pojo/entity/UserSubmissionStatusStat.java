package com.gusl.common.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gusl.common.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("user_submission_status_stat")
@EqualsAndHashCode(callSuper = true)
@Data
public class UserSubmissionStatusStat extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String status;

    private Long submissionCount;
}
