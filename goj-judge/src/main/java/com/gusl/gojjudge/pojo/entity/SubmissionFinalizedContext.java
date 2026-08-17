package com.gusl.gojjudge.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提交终态上下文，向个人中心等派生数据更新器传递一次完成的普通提交。
 */
@Data
@AllArgsConstructor
public class SubmissionFinalizedContext {

    /**
     * 提交 id。
     */
    private Long submissionId;

    /**
     * 提交用户 id。
     */
    private Long userId;

    /**
     * 题目 id。
     */
    private Long problemId;

    /**
     * 提交语言编码。
     */
    private String language;

    /**
     * 最终测评状态。
     */
    private String finalStatus;

    /**
     * 用户提交时间。
     */
    private LocalDateTime submissionTime;

    /**
     * 测评结束时间。
     */
    private LocalDateTime judgeEndTime;
}
