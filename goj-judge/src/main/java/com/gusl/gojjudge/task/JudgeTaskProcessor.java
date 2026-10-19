package com.gusl.gojjudge.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.constant.JudgeTaskStatus;
import com.gusl.common.pojo.entity.JudgeTask;
import com.gusl.common.pojo.entity.JudgeTaskMessage;
import com.gusl.gojjudge.judging.JudgeService;
import com.gusl.gojjudge.judging.result.SubmissionResultService;
import com.gusl.gojjudge.task.mapper.JudgeTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * 测评任务处理器，统一负责领取、执行、结果收尾和过期租约恢复。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeTaskProcessor {

    private final JudgeService judgeService;
    private final JudgeTaskMapper judgeTaskMapper;
    private final SubmissionResultService submissionResultService;
    private final TransactionTemplate transactionTemplate;

    /** 当前 Judge 实例标识。 */
    @Value("${goj.judge.worker-name}")
    private String workerName;

    /** 单次执行允许持有租约的时间。 */
    @Value("${goj.judge.task.processing-timeout-minutes:10}")
    private long processingTimeoutMinutes;

    /** 执行异常后的重试等待时间。 */
    @Value("${goj.judge.task.retry-initial-delay-seconds:10}")
    private long retryDelaySeconds;

    /** 原子领取任务，在数据库事务外执行判题，再收尾任务状态。 */
    public void process(JudgeTaskMessage message) {
        if (!claimTask(message)) {
            log.info("测评任务未领取，任务状态或消息已变化，taskId={}", message.getTaskId());
            return;
        }

        // 耗时的编译和运行不占用任务行锁或数据库事务。
        String errorMessage = null;
        try {
            judgeService.judge(message);
        } catch (Exception exception) {
            log.error("测评任务执行异常，taskId={}", message.getTaskId(), exception);
            errorMessage = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        }

        // 收尾失败时保留 PROCESSING，后续由租约恢复重新处理。
        try {
            complete(message, errorMessage);
        } catch (Exception exception) {
            log.error("测评任务状态收尾失败，等待租约恢复，taskId={}", message.getTaskId(), exception);
        }
    }

    /** 单条恢复使用短事务，提交已落库时只补齐成功状态。 */
    public void recover(Long taskId) {
        // TransactionTemplate 显式开启事务，同类方法调用也不会绕过事务管理。
        transactionTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            JudgeTask task = judgeTaskMapper.selectOne(
                    Wrappers.<JudgeTask>lambdaQuery()
                            .eq(JudgeTask::getId, taskId)
                            .eq(JudgeTask::getStatus, JudgeTaskStatus.PROCESSING)
                            .le(JudgeTask::getLeaseExpireTime, now)
                            .last("FOR UPDATE")
            );
            if (task != null) {
                finish(task, "Worker 租约过期", now);
            }
        });
    }

    /** 领取任务, 条件更新 */
    private boolean claimTask(JudgeTaskMessage message) {
        LocalDateTime now = LocalDateTime.now();
        return judgeTaskMapper.update(
                Wrappers.<JudgeTask>lambdaUpdate()
                        .set(JudgeTask::getStatus, JudgeTaskStatus.PROCESSING)
                        .setSql("attempt_count = attempt_count + 1")
                        .set(JudgeTask::getLeaseOwner, workerName)
                        .set(JudgeTask::getLeaseExpireTime, now.plusMinutes(processingTimeoutMinutes))
                        .set(JudgeTask::getNextRetryTime, null)
                        .set(JudgeTask::getLastError, null)
                        .eq(JudgeTask::getId, message.getTaskId())
                        .eq(JudgeTask::getTaskType, message.getTaskType())
                        .eq(JudgeTask::getBusinessId, message.getBusinessId())
                        .eq(JudgeTask::getTaskVersion, message.getTaskVersion())
                        // 本身就是pending状态的任务, 重试的任务. 
                        .and(wrapper -> wrapper
                                .eq(JudgeTask::getStatus, JudgeTaskStatus.PENDING)
                                .or(retryWrapper -> retryWrapper
                                        .eq(JudgeTask::getStatus, JudgeTaskStatus.RETRY_WAIT)
                                        .le(JudgeTask::getNextRetryTime, now))
                        )
        ) == 1;
    }

    /** 锁定仍归当前 Worker 的任务，与过期恢复互斥收尾。 */
    private void complete(JudgeTaskMessage message, String errorMessage) {
        transactionTemplate.executeWithoutResult(status -> {
            JudgeTask task = judgeTaskMapper.selectOne(
                    Wrappers.<JudgeTask>lambdaQuery()
                            .eq(JudgeTask::getId, message.getTaskId())
                            .eq(JudgeTask::getTaskType, message.getTaskType())
                            .eq(JudgeTask::getBusinessId, message.getBusinessId())
                            .eq(JudgeTask::getTaskVersion, message.getTaskVersion())
                            .eq(JudgeTask::getStatus, JudgeTaskStatus.PROCESSING)
                            .eq(JudgeTask::getLeaseOwner, workerName)
                            .last("FOR UPDATE")
            );
            if (task != null) {
                finish(task, errorMessage, LocalDateTime.now().plusSeconds(retryDelaySeconds));
            }
        });
    }

    /** 调用方已持有任务行锁，统一决定成功、重试或死亡并清理租约。 */
    private void finish(JudgeTask task, String errorMessage, LocalDateTime retryTime) {
        // 判题正常返回意味着结果已落库；异常或恢复路径需要检查是否仅任务状态遗漏。
        // 个人统计与结果同事务，统计失败会回滚结果，不会被这里当作成功。
        boolean succeeded = errorMessage == null || judgeService.isBusinessTerminal(task.getTaskType(), task.getBusinessId());
        boolean dead = !succeeded && task.getAttemptCount() >= task.getMaxAttempts();
        String nextStatus = succeeded ? JudgeTaskStatus.SUCCEEDED : (dead ? JudgeTaskStatus.DEAD : JudgeTaskStatus.RETRY_WAIT);

        int affectedRows = judgeTaskMapper.update(
                Wrappers.<JudgeTask>lambdaUpdate()
                        .set(JudgeTask::getStatus, nextStatus)
                        .set(JudgeTask::getLeaseOwner, null)
                        .set(JudgeTask::getLeaseExpireTime, null)
                        .set(JudgeTask::getNextRetryTime, succeeded || dead ? null : retryTime)
                        .set(JudgeTask::getLastDispatchTime, null)
                        .set(JudgeTask::getLastError, succeeded ? null : errorMessage)
                        .eq(JudgeTask::getId, task.getId())
        );
        if (affectedRows != 1) {
            throw new IllegalStateException("测评任务状态更新失败：" + task.getId());
        }

        // DEAD 与提交的系统错误必须一起提交；写回异常由短事务统一回滚。
        if (dead) {
            submissionResultService.markSystemError(task.getTaskType(), task.getBusinessId(), errorMessage);
            log.error("测评任务达到最大执行次数，taskId={}，attemptCount={}", task.getId(), task.getAttemptCount());
        }
    }
}
