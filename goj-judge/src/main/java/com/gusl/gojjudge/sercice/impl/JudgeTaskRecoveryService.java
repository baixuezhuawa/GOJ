package com.gusl.gojjudge.sercice.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.constant.JudgeTaskStatus;
import com.gusl.common.pojo.entity.JudgeTask;
import com.gusl.gojjudge.mapper.JudgeTaskMapper;
import com.gusl.gojjudge.sercice.JudgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 测评任务租约恢复服务，负责修复超过租约时间的 PROCESSING 任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeTaskRecoveryService {

    private final JudgeTaskMapper judgeTaskMapper;

    private final JudgeService judgeService;

    private final JudgeTaskFailureService failureService;

    /**
     * 恢复一条超过租约时间的任务。
     *
     * @param taskId 任务id
     */
    @Transactional(rollbackFor = Exception.class)
    public void recover(Long taskId) {
        LocalDateTime now = LocalDateTime.now();
        JudgeTask judgeTask = judgeTaskMapper.selectById(taskId);

        if (judgeTask == null
                || !JudgeTaskStatus.PROCESSING.equals(judgeTask.getStatus())
                || judgeTask.getLeaseExpireTime() == null
                || judgeTask.getLeaseExpireTime().isAfter(now)) {
            return;
        }

        // 业务终态已经落库时，只需要补齐 judge_task 的成功状态。
        if (judgeService.isBusinessTerminal(judgeTask.getTaskType(), judgeTask.getBusinessId())) {
            int affectedRows = judgeTaskMapper.update(
                    Wrappers.<JudgeTask> lambdaUpdate()
                            .set(JudgeTask::getStatus, JudgeTaskStatus.SUCCEEDED)
                            .set(JudgeTask::getLeaseOwner, null)
                            .set(JudgeTask::getLeaseExpireTime, null)
                            .set(JudgeTask::getNextRetryTime, null)
                            .set(JudgeTask::getLastError, null)
                            .eq(JudgeTask::getId, taskId)
                            .eq(JudgeTask::getStatus, JudgeTaskStatus.PROCESSING)
                            .eq(JudgeTask::getLeaseOwner, judgeTask.getLeaseOwner())
                            .le(JudgeTask::getLeaseExpireTime, now)
            );

            if (affectedRows != 1) {
                log.info("租约过期任务未补齐成功状态，可能已经被其他线程处理，taskId={}", taskId);
            }
            return;
        }

        // attemptCount 已在领取任务时增加，恢复时只决定重试或死亡。
        failureService.handleExpiredLease(judgeTask, now);
    }

}
