package com.gusl.gojjudge.sercice.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.constant.JudgeTaskStatus;
import com.gusl.common.constant.JudgeTaskType;
import com.gusl.common.pojo.entity.JudgeTask;
import com.gusl.common.pojo.entity.JudgeTaskMessage;
import com.gusl.gojjudge.mapper.JudgeTaskMapper;
import com.gusl.gojjudge.sercice.JudgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;


/**
 * 测评任务处理器，负责原子领取任务并调用对应业务测评入口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeTaskProcessor {

    private final JudgeService judgeService;

    private final JudgeTaskMapper judgeTaskMapper;

    private final JudgeTaskFailureService failureService;

    /**
     * 当前 Judge 实例标识。
     */
    @Value("${goj.judge.worker-name}")
    private String workerName;

    /**
     * 任务租约时间。
     */
    @Value("${goj.judge.task.processing-timeout-minutes:10}")
    private long processingTimeoutMinutes;

    /**
     * 领取任务
     * @param message 任务信息
     */
    public void process(JudgeTaskMessage message) {
        // 使用数据库条件更新原子领取任务。
        if (!claimTask(message)) {
            log.info("测评任务未领取，可能已经被其他 Worker 处理，taskId={}", message.getTaskId());
            return;
        }

        try {
            // 根据任务类型执行对应测评。
            executeBusinessTask(message);

            // 测评正常结束，任务标记为成功。
            markSucceeded(message.getTaskId());

        } catch (Exception exception) {
            log.error("测评任务执行出现系统异常，taskId={}", message.getTaskId(), exception);

            try {
                // 根据执行次数决定等待重试还是进入死信状态。
                failureService.handleFailure(message, exception);

            } catch (Exception updateException) {
                log.error("更新测评任务失败状态异常，taskId={}", message.getTaskId(), updateException);
            }
        }
    }



    /**
     * 原子领取测评任务。
     *
     * @param message 测评任务消息
     * @return 是否领取成功
     */
    private boolean claimTask(JudgeTaskMessage message) {
        LocalDateTime now = LocalDateTime.now();

        int affectedRows = judgeTaskMapper.update(
                Wrappers.<JudgeTask>lambdaUpdate()
                        // 领取后进入执行状态。
                        .set(JudgeTask::getStatus, JudgeTaskStatus.PROCESSING)
                        .setSql("attempt_count = attempt_count + 1")
                        .set(JudgeTask::getLeaseOwner, workerName)
                        .set(
                                JudgeTask::getLeaseExpireTime,
                                now.plusMinutes(processingTimeoutMinutes)
                        )
                        .set(JudgeTask::getNextRetryTime, null)
                        .set(JudgeTask::getLastError, null)

                        // 消息必须与数据库中的任务完全一致。
                        .eq(JudgeTask::getId, message.getTaskId())
                        .eq(JudgeTask::getTaskType, message.getTaskType())
                        .eq(JudgeTask::getBusinessId, message.getBusinessId())
                        .eq(JudgeTask::getTaskVersion, message.getTaskVersion())

                        // 首次任务可以直接领取，重试任务必须到达重试时间。
                        .and(wrapper -> wrapper
                                .eq(JudgeTask::getStatus, JudgeTaskStatus.PENDING)
                                .or(retryWrapper -> retryWrapper
                                        .eq(JudgeTask::getStatus, JudgeTaskStatus.RETRY_WAIT)
                                        .le(JudgeTask::getNextRetryTime, now)
                                )
                        )
        );

        return affectedRows == 1;
    }



    /**
     * 根据任务类型调用对应测评入口。
     *
     * @param message 测评任务消息
     */
    private void executeBusinessTask(JudgeTaskMessage message) {
        if (JudgeTaskType.SUBMISSION.equals(message.getTaskType())) {
            judgeService.judgeSubmission(message.getBusinessId());
            return;
        }

        if (JudgeTaskType.PROBLEM_REVIEW.equals(message.getTaskType())) {
            judgeService.judgeProblemReview(message.getBusinessId());
            return;
        }

        throw new IllegalArgumentException("不支持的测评任务类型：" + message.getTaskType());
    }



    /**
     * 将执行完成的任务标记为成功。
     *
     * @param taskId 测评任务 id
     */
    private void markSucceeded(Long taskId) {
        int affectedRows = judgeTaskMapper.update(
                Wrappers.<JudgeTask>lambdaUpdate()
                        .set(JudgeTask::getStatus, JudgeTaskStatus.SUCCEEDED)
                        .set(JudgeTask::getLeaseOwner, null)
                        .set(JudgeTask::getLeaseExpireTime, null)
                        .set(JudgeTask::getNextRetryTime, null)
                        .set(JudgeTask::getLastError, null)
                        .eq(JudgeTask::getId, taskId)
                        .eq(JudgeTask::getStatus, JudgeTaskStatus.PROCESSING)
                        .eq(JudgeTask::getLeaseOwner, workerName)
        );

        if (affectedRows != 1) {
            log.warn("测评任务成功状态更新失败，taskId={}", taskId);
        }
    }



}
