package com.gusl.common.constant;

/**
 * 题目测试数据集状态常量。
 */
public final class ProblemTestDataStatus {

    private ProblemTestDataStatus() {
    }

    /** 上传中 */
    public static final String UPLOADING = "UPLOADING";

    /** 上传结束 */
    public static final String UPLOADED = "UPLOADED";

    /** 已提取 */
    public static final String EXTRACTED = "EXTRACTED";

    /** 非法 */
    public static final String INVALID = "INVALID";

    /** 已完成校验并发布到正式目录上, 可以被启用 */
    public static final String READY = "READY";

    /** 已撤销, 不允许用于普通测评 */
    public static final String RETIRED = "RETIRED";
}
