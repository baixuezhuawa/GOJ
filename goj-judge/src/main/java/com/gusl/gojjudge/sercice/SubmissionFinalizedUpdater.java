package com.gusl.gojjudge.sercice;

import com.gusl.gojjudge.pojo.entity.SubmissionFinalizedContext;

/**
 * 提交终态派生数据更新器，用于扩展普通提交完成后的统计维护逻辑。
 */
public interface SubmissionFinalizedUpdater {

    /**
     * 根据一次成功写入的提交终态更新派生数据。
     *
     * @param context 提交终态上下文
     */
    void update(SubmissionFinalizedContext context);
}
