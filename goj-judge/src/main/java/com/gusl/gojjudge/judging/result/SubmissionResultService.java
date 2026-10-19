package com.gusl.gojjudge.judging.result;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.constant.SubmissionType;
import com.gusl.gojjudge.judging.exception.JudgeSystemException;
import com.gusl.common.constant.SystemConstant;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojjudge.judging.mapper.SubmissionMapper;
import com.gusl.gojjudge.judging.model.JudgeOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提交结果写回服务，更新统一提交表并维护普通提交的终态统计。
 */
@Component
@RequiredArgsConstructor
public class SubmissionResultService {

    private final SubmissionMapper submissionMapper;

    /** 所有更新器 */
    private final List<SubmissionFinalizedUpdater> submissionFinalizedUpdaters;


    /**
     * 写回当前测评结果，普通提交第一次进入终态时同步更新全部派生统计。
     *
     * @param submission 当前提交
     * @param outcome 当前测评结果
     */
    @Transactional(rollbackFor = Exception.class) // 统一进行事务回滚
    public boolean updateSubmission(Submission submission, JudgeOutcome outcome) {
        boolean terminal = JudgingConstant.TERMINAL_STATUSES.contains(outcome.getCurStatus());

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
                .notIn(Submission::getStatus, JudgingConstant.TERMINAL_STATUSES); // 判断非终态

        // 任何后续写回都不能覆盖终态，终态重复消费也不会再次累加统计。
        int affectedRows = submissionMapper.update(updateWrapper);
        if (affectedRows != 1) {
            Submission current = submissionMapper.selectById(submission.getId());
            return current != null && JudgingConstant.TERMINAL_STATUSES.contains(current.getStatus());
        }
        if (!terminal) {
            return true;
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

        // 仅普通提交的非系统错误结果进入个人统计，比赛和验题保持独立。
        if (
                !SubmissionType.REGULAR.equals(submission.getSubmissionType()) ||
                SystemConstant.SYSTEM_ERROR.equals(outcome.getCurStatus())
        ) {
            return true;
        }

        // 遍历每个需要终态更新器, 更新需要的表
        for (SubmissionFinalizedUpdater updater : submissionFinalizedUpdaters) {
            updater.update(context);
        }

        return true;
    }

    /** 系统错误也通过统一提交表写回。 */
    @Transactional(rollbackFor = Exception.class)
    public void markSystemError(String taskType, Long businessId, String errorMessage) {
        Submission submission = submissionMapper.selectById(businessId);
        if (submission == null) {
            throw new JudgeSystemException("提交不存在：" + businessId);
        }
        JudgeOutcome outcome = new JudgeOutcome();
        outcome.setCurStatus(SystemConstant.SYSTEM_ERROR);
        outcome.setJudgeMsg(errorMessage);
        if (!updateSubmission(submission, outcome)) {
            throw new JudgeSystemException("系统错误终态写回被拒绝：" + businessId);
        }
    }
}
