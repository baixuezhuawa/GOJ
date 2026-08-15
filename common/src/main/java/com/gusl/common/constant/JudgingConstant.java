package com.gusl.common.constant;

/**
 * 测评相关常量量
 */
public final class JudgingConstant {

    // 运行状态
    /** 处于队列 */
    public static final String IN_QUEUE = "in queue";

    /** 等待 */
    public static final String WAIT = "wait";

    /** 编译中 */
    public static final String COMPILE = "compiling";

    /** 运行中 */
    public static final String RUNNING = "running";


    // 最终结果, 对齐沙箱的响应结果
    /** 编译错误 */
    public static final String COMPILE_ERROR = "Compile Error";

    /** 错误答案 */
    public static final String WRONG_ANSWER = "Wrong Answer";

    /** 错误正确 */
    public static final String ACCEPTED = "Accepted";

    /** 超时 */
    public static final String TIME_LIMIT_EXCEEDED = "Time Limit Exceeded";

    /** 内存超出限制 */
    public static final String MEMORY_LIMIT_EXCEEDED = "Memory Limit Exceeded";

    /** 运行时错误 */
    public static final String RUNTIME_ERROR = "Runtime Error";

}
