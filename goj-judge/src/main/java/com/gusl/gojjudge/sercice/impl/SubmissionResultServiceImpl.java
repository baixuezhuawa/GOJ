package com.gusl.gojjudge.sercice.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.constant.SystemConstant;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojjudge.mapper.SubmissionMapper;
import com.gusl.gojjudge.pojo.entity.JudgeOutcome;
import com.gusl.gojjudge.pojo.entity.SubmissionFinalizedContext;
import com.gusl.gojjudge.sercice.SubmissionFinalizedUpdater;
import com.gusl.gojjudge.sercice.SubmissionResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 普通提交结果写回服务实现，统一处理状态更新和终态派生数据维护。
 */
@Service
@RequiredArgsConstructor
public class SubmissionResultServiceImpl implements SubmissionResultService {

    /** 终态集合，用于防止同一提交重复触发统计更新。 */
    private static final Set<String> TERMINAL_STATUSES = new HashSet<>(Arrays.asList(
            JudgingConstant.COMPILE_ERROR,
            JudgingConstant.WRONG_ANSWER,
            JudgingConstant.ACCEPTED,
            JudgingConstant.TIME_LIMIT_EXCEEDED,
            JudgingConstant.MEMORY_LIMIT_EXCEEDED,
            JudgingConstant.RUNTIME_ERROR,
            SystemConstant.SYSTEM_ERROR
    ));

    private final SubmissionMapper submissionMapper;

    /**
     * 获取到所有更新器
     */
    private final List<SubmissionFinalizedUpdater> submissionFinalizedUpdaters;

    /**
     * 写回当前测评结果，普通提交第一次进入终态时同步更新全部派生统计。
     *
     * @param submission 当前普通提交
     * @param outcome 当前测评结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class) // 统一进行事务回滚
    public void updateSubmission(Submission submission, JudgeOutcome outcome) {
        boolean terminal = TERMINAL_STATUSES.contains(outcome.getCurStatus());
        LocalDateTime judgeEndTime = terminal ? LocalDateTime.now() : null;

        // 构造提交状态和测评结果的统一更新条件。
        LambdaUpdateWrapper<Submission> updateWrapper = Wrappers.<Submission>lambdaUpdate()
                .set(Submission::getStatus, outcome.getCurStatus())
                .set(outcome.getTimeMs() != null, Submission::getTimeMs, outcome.getTimeMs())
                .set(outcome.getMemoryKb() != null, Submission::getMemoryKb, outcome.getMemoryKb())
                .set(outcome.getCompilerMsg() != null, Submission::getCompilerMsg, outcome.getCompilerMsg())
                .set(outcome.getJudgeMsg() != null, Submission::getJudgeMsg, outcome.getJudgeMsg())
                .set(outcome.getScore() != null, Submission::getScore, outcome.getScore())
                .set(terminal, Submission::getJudgeEndTime, judgeEndTime)
                .eq(Submission::getId, submission.getId())
                .notIn(Submission::getStatus, TERMINAL_STATUSES);

        // 任何后续写回都不能覆盖终态，终态重复消费也不会再次累加统计。
        int affectedRows = submissionMapper.update(updateWrapper);
        if (!terminal || affectedRows != 1) {
            return;
        }

        // 终态写入成功后，在同一事务中通知已注册的派生数据更新器。
        SubmissionFinalizedContext context = new SubmissionFinalizedContext(
                submission.getId(),
                submission.getUserId(),
                submission.getProblemId(),
                submission.getLanguage(),
                outcome.getCurStatus(),
                submission.getSubmissionTime(),
                judgeEndTime
        );
        for (SubmissionFinalizedUpdater updater : submissionFinalizedUpdaters) {
            updater.update(context);
        }
    }
}
