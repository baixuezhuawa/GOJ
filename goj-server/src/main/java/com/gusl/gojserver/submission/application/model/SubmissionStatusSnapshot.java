package com.gusl.gojserver.submission.application.model;

import com.gusl.gojserver.submission.application.vo.SubmissionStatusVo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 提交状态快照，额外保存归属信息及最后一次主库核验时间。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SubmissionStatusSnapshot extends SubmissionStatusVo {

    private String submissionType;

    private Long contestId;

    private Long userId;

    @EqualsAndHashCode.Exclude
    private long verifiedAt;

}
