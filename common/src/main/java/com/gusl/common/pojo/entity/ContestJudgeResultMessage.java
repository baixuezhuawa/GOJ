package com.gusl.common.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** 比赛最终结果消息，仅携带当前提交，其他提交由 Server 内存维护。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContestJudgeResultMessage {

    private Long contestId;

    private Long problemId;

    private Long userId;

    private Long submissionId;

    private Integer version;

    private String status;

    private LocalDateTime submissionTime;

    /** 从已落库的比赛提交构造通知。 */
    public static ContestJudgeResultMessage from(Submission submission) {
        return new ContestJudgeResultMessage(
                submission.getContestId(),
                submission.getProblemId(),
                submission.getUserId(),
                submission.getId(),
                submission.getVersion(),
                submission.getStatus(),
                submission.getSubmissionTime()
        );
    }
}
