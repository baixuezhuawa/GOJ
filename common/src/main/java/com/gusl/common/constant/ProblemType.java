package com.gusl.common.constant;

/** 题目类型常量，区分普通比对、答案不唯一和交互题。 */
public final class ProblemType {

    private ProblemType() {
    }

    /** 普通题，直接比对选手输出。 */
    public static final Integer NORMAL = 0;

    /** 答案不唯一，先运行校验程序再比对其输出。 */
    public static final Integer SPECIAL = 1;

    /** 交互题，预留类型，暂未实现执行策略。 */
    public static final Integer INTERACTION = 2;
}
