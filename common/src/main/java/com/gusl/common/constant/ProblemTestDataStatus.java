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

    /** 启用中 */
    public static final String READY = "READY";

    /** 非启用 */
    public static final String RETIRED = "RETIRED";
}
