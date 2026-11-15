package com.gusl.common.constant;

/**
 * Judge Redis 队列名称。
 */
public final class JudgeQueueConstant {

    private JudgeQueueConstant() {
    }

    /** 普通用户提交队列。 */
    public static final String SUBMISSION_READY_QUEUE = "goj:judge:ready";

    /** 管理员验题提交队列。 */
    public static final String PROBLEM_REVIEW_READY_QUEUE = "goj:judge:problem-review:ready";

    /** 比赛队列 */
    public static final String CONTEST_READY_QUEUE = "goj:judge:contest-ready";
}
