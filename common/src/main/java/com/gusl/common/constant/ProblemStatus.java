package com.gusl.common.constant;

/**
 * 题目状态常量。
 */
public final class ProblemStatus {

    private ProblemStatus() {
    }

    /** 草稿。 */
    public static final Integer DRAFT = 0;

    /** 已发布。 */
    public static final Integer PUBLISH = 1;

    /** 已停用。 */
    public static final Integer DISABLE = 2;

    /** 待管理员审核。 */
    public static final Integer PENDING = 3;

    /** 审核未通过，已退回给作者。 */
    public static final Integer WITHDRAW = 4;
}
