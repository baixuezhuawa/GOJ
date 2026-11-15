package com.gusl.common.constant;

/**
 * 比赛阶段常量
 */
public class ContestStatus {

    /** 草稿阶段 */
    public static final String DRAFT = "DRAFT";

    /** 已安排 */
    public static final String SCHEDULED = "SCHEDULED";

    /** 进行中 */
    public static final String RUNNING = "RUNNING";

    /** 结束后等待, 剩余提交测评结束 */
    public static final String WAITING = "WAITING";

    /** 开启hack阶段 */
    public static final String HACKING = "HACKING";

    /** 系统测试/回滚 */
    public static final String SYSTEM_TESTING = "SYSTEM_TESTING";

    /** 彻底结束 */
    public static final String FINISH = "FINISH";
}
