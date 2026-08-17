package com.gusl.gojjudge.sercice.updater;

import com.gusl.common.constant.JudgingConstant;
import com.gusl.gojjudge.mapper.UserActivityDayMapper;
import com.gusl.gojjudge.mapper.UserProblemProgressMapper;
import com.gusl.gojjudge.pojo.entity.SubmissionFinalizedContext;
import com.gusl.gojjudge.sercice.SubmissionFinalizedUpdater;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 用户日期活跃更新器，用于维护每天的 Accepted 数量和新解决题目数量。
 */
@Component
@Order(200)
@RequiredArgsConstructor
public class UserActivityDayUpdater implements SubmissionFinalizedUpdater {

    private final UserActivityDayMapper activityDayMapper;

    private final UserProblemProgressMapper problemProgressMapper;

    /**
     * Accepted 提交完成后累加其提交日期对应的活跃数据。
     *
     * @param context 提交终态上下文
     */
    @Override
    public void update(SubmissionFinalizedContext context) {
        if (!JudgingConstant.ACCEPTED.equals(context.getFinalStatus())) {
            return;
        }

        // 题目进度更新器先写入第一次通过提交，再据此判断当天是否新增解决题目。
        Long firstAcceptedSubmissionId = problemProgressMapper.selectFirstAcceptedSubmissionId(
                context.getUserId(),
                context.getProblemId()
        );
        int newSolvedProblemIncrement = Objects.equals(
                firstAcceptedSubmissionId,
                context.getSubmissionId()
        ) ? 1 : 0;

        activityDayMapper.increase(
                context.getUserId(),
                context.getSubmissionTime().toLocalDate(),
                newSolvedProblemIncrement
        );
    }
}
