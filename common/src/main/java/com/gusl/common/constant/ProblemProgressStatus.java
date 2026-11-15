package com.gusl.common.constant;

/**
 * 用户题目进度状态常量。
 */
public final class ProblemProgressStatus {

    private ProblemProgressStatus() {
    }

    /** 未尝试，数据库中不存在进度记录时使用。 */
    public static final String UNATTEMPTED = "UNATTEMPTED";

    /** 已尝试但尚未通过。 */
    public static final String ATTEMPTED = "ATTEMPTED";

    /** 已通过。 */
    public static final String SOLVED = "SOLVED";
}
