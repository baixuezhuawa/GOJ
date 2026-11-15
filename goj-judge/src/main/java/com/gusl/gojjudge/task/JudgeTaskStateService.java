package com.gusl.gojjudge.task;

import com.gusl.gojjudge.judging.JudgeService;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.constant.JudgeTaskStatus;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojjudge.task.mapper.JudgeTaskMapper;
import com.gusl.gojjudge.judging.result.SubmissionResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 测评任务状态服务，统一维护成功、重试、死亡及租约恢复后的状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeTaskStateService {

    private final JudgeTaskMapper judgeTaskMapper;

    private final JudgeService judgeService;

    private final SubmissionResultService submissionResultService;


    /** 当前 Judge 实例标识。 */
    @Value("${goj.judge.worker-name}")
    private String workerName;

    /** 系统异常后的重试等待时间。 */
    @Value("${goj.judge.task.retry-initial-delay-seconds:10}")
    private long retryDelaySeconds;


    /**
     * 处理 Worker 执行期间出现的系统异常。
     *
     * @param message 测评任务消息
     * @param exception 系统异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleFailure(JudgeTaskMessage message, Exception exception) {
        JudgeTask task = judgeTaskMapper.selectById(message.getTaskId());
        if (task == null) {
            log.error("测评任务不存在，taskId={}", message.getTaskId());
            return;
        }

        // 只允许当前 Worker 处理自己仍然持有租约的这一次任务。
        if (!JudgeTaskStatus.PROCESSING.equals(task.getStatus())
                || !Objects.equals(workerName, task.getLeaseOwner())
                || !Objects.equals(message.getTaskType(), task.getTaskType())
                || !Objects.equals(message.getBusinessId(), task.getBusinessId())
                || !Objects.equals(message.getTaskVersion(), task.getTaskVersion())) {
            log.info("测评任务失败状态未处理，当前 Worker 已不再持有该任务，taskId={}", task.getId());
            return;
        }

        // 在测评处于终态后, 更新用户个人信息的时候, 出现了问题, 但是问题已经是处于终态了, 不需要投递, 也标记为成功.
        // TODO 更新用户个人信息, 应该交个异步任务去执行, 不至于更新失败, 会影响到测评链路.
        if (judgeService.isBusinessTerminal(task.getTaskType(), task.getBusinessId())) {
            markSucceeded(task.getId(), task.getLeaseOwner(), null);
            return;
        }

        String errorMessage = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();

        transitionFailure(task, errorMessage, LocalDateTime.now().plusSeconds(retryDelaySeconds), null);
    }


    /** 恢复单条过期任务；独立事务保证死亡状态与业务系统错误一起提交。 */
    @Transactional(rollbackFor = Exception.class)
    public void recover(Long taskId) {
        LocalDateTime now = LocalDateTime.now();
        JudgeTask task = judgeTaskMapper.selectById(taskId);
        if (
                    task == null
                || !JudgeTaskStatus.PROCESSING.equals(task.getStatus())
                || task.getLeaseExpireTime() == null
                || task.getLeaseExpireTime().isAfter(now)
        ) {
            return;
        }
        // 提交已完成则只补齐任务状态，不重复运行选手程序。
        if (judgeService.isBusinessTerminal(task.getTaskType(), task.getBusinessId())) {
            markSucceeded(task.getId(), task.getLeaseOwner(), now);
            return;
        }
        transitionFailure(task, "Worker 租约过期", now, now);
    }


    /** 正常执行结束后，标记当前 Worker 持有的任务成功。 */
    public void markSucceeded(Long taskId) {
        markSucceeded(taskId, workerName, null);
    }


    /**
     * 根据已经执行的次数把任务转为等待重试或死亡状态。
     *
     * <p>attemptCount 在领取任务时增加 </p>
     */
    private void transitionFailure(
            JudgeTask task,
            String errorMessage,
            LocalDateTime retryTime,
            LocalDateTime expiredBefore
    ) {
        boolean dead = task.getAttemptCount() >= task.getMaxAttempts();

        LambdaUpdateWrapper<JudgeTask> updateWrapper = Wrappers.<JudgeTask>lambdaUpdate()
                .set(JudgeTask::getStatus, dead
                        ? JudgeTaskStatus.DEAD
                        : JudgeTaskStatus.RETRY_WAIT)
                .set(JudgeTask::getNextRetryTime, dead ? null : retryTime)
                .set(JudgeTask::getLastDispatchTime, null)
                .set(JudgeTask::getLastError, errorMessage)
                .set(JudgeTask::getLeaseOwner, null)
                .set(JudgeTask::getLeaseExpireTime, null)
                .eq(JudgeTask::getId, task.getId())
                .eq(JudgeTask::getStatus, JudgeTaskStatus.PROCESSING)
                .eq(JudgeTask::getLeaseOwner, task.getLeaseOwner())
                .le(expiredBefore != null, JudgeTask::getLeaseExpireTime, expiredBefore);

        int affectedRows = judgeTaskMapper.update(updateWrapper);
        if (affectedRows != 1) {
            log.info("测评任务失败状态未更新，可能已经被其他线程处理，taskId={}", task.getId());
            return;
        }

        if (!dead) {
            return;
        }

        log.error("测评任务达到最大执行次数，taskId={}，attemptCount={}",
                task.getId(),
                task.getAttemptCount()
        );

        // 与 judge_task 的 DEAD 状态处于同一个数据库事务中。
        submissionResultService.markSystemError(task.getTaskType(), task.getBusinessId(), errorMessage);
    }


    /** 共用成功更新条件；恢复路径额外检查租约仍然过期。 */
    private void markSucceeded(Long taskId, String leaseOwner, LocalDateTime expiredBefore) {
        int affectedRows = judgeTaskMapper.update(
                Wrappers.<JudgeTask>lambdaUpdate()
                        .set(JudgeTask::getStatus, JudgeTaskStatus.SUCCEEDED)
                        .set(JudgeTask::getLeaseOwner, null)
                        .set(JudgeTask::getLeaseExpireTime, null)
                        .set(JudgeTask::getNextRetryTime, null)
                        .set(JudgeTask::getLastError, null)
                        .eq(JudgeTask::getId, taskId)
                        .eq(JudgeTask::getStatus, JudgeTaskStatus.PROCESSING)
                        .eq(JudgeTask::getLeaseOwner, leaseOwner)
                        .le(expiredBefore != null, JudgeTask::getLeaseExpireTime, expiredBefore)
        );
        if (affectedRows != 1) {
            log.info("测评任务成功状态未更新，可能已经被其他线程处理，taskId={}", taskId);
        }
    }
}
