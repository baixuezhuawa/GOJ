package com.gusl.gojjudge.sercice.updater;

import com.gusl.gojjudge.mapper.UserSubmissionStatusStatMapper;
import com.gusl.gojjudge.pojo.entity.SubmissionFinalizedContext;
import com.gusl.gojjudge.sercice.SubmissionFinalizedUpdater;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户提交状态统计更新器，用于按照最终测评状态累加提交数量。
 */
@Component
@RequiredArgsConstructor
public class UserSubmissionStatusStatUpdater implements SubmissionFinalizedUpdater {

    private final UserSubmissionStatusStatMapper submissionStatusStatMapper;

    /**
     * 累加一次已完成提交对应的状态统计。
     *
     * @param context 提交终态上下文
     */
    @Override
    public void update(SubmissionFinalizedContext context) {
        submissionStatusStatMapper.increase(context.getUserId(), context.getFinalStatus());
    }
}
