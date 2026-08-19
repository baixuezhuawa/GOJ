package com.gusl.gojjudge.sercice.updater;

import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.constant.ProblemProgressStatus;
import com.gusl.gojjudge.mapper.UserProblemProgressMapper;
import com.gusl.gojjudge.pojo.entity.SubmissionFinalizedContext;
import com.gusl.gojjudge.sercice.SubmissionFinalizedUpdater;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 用户问题进度更新者
 */
@Component
@Order(100)
@RequiredArgsConstructor
public class UserProblemProgressUpdater implements SubmissionFinalizedUpdater {

    private final UserProblemProgressMapper problemProgressMapper;

    @Override
    public void update(SubmissionFinalizedContext context) {
        // Accepted 提交推进为已解决，其他终态只记录为已尝试。
        boolean accepted = JudgingConstant.ACCEPTED.equals(context.getFinalStatus());
        problemProgressMapper.increase(
                context.getUserId(),
                context.getProblemId(),
                accepted ? ProblemProgressStatus.SOLVED : ProblemProgressStatus.ATTEMPTED,
                context.getSubmissionId(),
                context.getSubmissionTime(),
                accepted ? 1 : 0,
                context.getUserId().toString()
        );
    }
}
