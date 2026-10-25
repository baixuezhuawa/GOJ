package com.gusl.common.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** ContestSubmissionHistory，保存赛后归档数据。 */
@Data
@TableName("contest_submission_history")
public class ContestSubmissionHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contestId;

    private Long sequenceNo;

    private Long submissionId;

    private Long userId;

    private Long problemId;

    private Long elapsedMillis;

    private String status;
}
