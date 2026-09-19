package com.gusl.gojjudge.task.application;

import com.gusl.gojjudge.judging.application.JudgeService;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.constant.JudgeTaskStatus;
import com.gusl.common.constant.JudgeTaskType;
import com.gusl.common.constant.SystemConstant;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojjudge.judging.infrastructure.persistence.ContestSubmissionMapper;
import com.gusl.gojjudge.task.infrastructure.persistence.JudgeTaskMapper;
import com.gusl.gojjudge.judging.infrastructure.persistence.ProblemReviewSubmissionMapper;
import com.gusl.gojjudge.judging.infrastructure.persistence.SubmissionMapper;
import com.gusl.gojjudge.judging.domain.model.JudgeOutcome;
import com.gusl.gojjudge.judging.application.result.SubmissionResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 测评任务失败处理服务，原子维护任务状态和业务系统错误终态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeTaskFailureService {

    private final JudgeTaskMapper judgeTaskMapper;

    private final JudgeService judgeService;

    private final SubmissionResultService submissionResultService;

    private final SubmissionMapper submissionMapper;
    private final ContestSubmissionMapper contestSubmissionMapper;
    private final ProblemReviewSubmissionMapper reviewSubmissionMapper;

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

        /*
        像是在一个测评结束后, submission表已经更新好了, 没有发生系统问题.
        但是在更新JudgeTask表的阶段, 出现MySQL宕机, 连接中断等情况.
        导致业务实际已经完成(submission测评已经结束并更新完成为终态), 但JudgeTask还没有更新好.
        出现状态不一致的情况(submission(finish), judgeTask(processing)).
        下面这个判断就为了解决这个数据不一致时, 防止重复测评的判断.
         */
        if (judgeService.isBusinessTerminal(task.getTaskType(), task.getBusinessId())) {
            markSucceeded(task);
            return;
        }

        String errorMessage = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();

        transitionFailure(
                task,
                errorMessage,
                LocalDateTime.now().plusSeconds(retryDelaySeconds),
                null
        );
    }

    /**
     * 处理租约已经过期的任务。
     *
     * @param task 扫描到的任务快照
     * @param expiredBefore 本次扫描的过期时间边界
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleExpiredLease(JudgeTask task, LocalDateTime expiredBefore) {
        transitionFailure(
                task,
                "Worker 租约过期",
                LocalDateTime.now(),
                expiredBefore
        );
    }

    /**
     * 根据已经执行的次数把任务转为等待重试或死亡状态。
     *
     * <p>attemptCount 已经在任务领取时增加，这里不能再次增加。</p>
     *
     * @param task 当前任务
     * @param errorMessage 错误信息
     * @param retryTime 未达到最大次数时的下次重试时间
     * @param expiredBefore 非空时要求任务租约已经过期
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
        markSystemError(task.getTaskType(), task.getBusinessId(), errorMessage);
    }

    /**
     * 当业务终态已经落库时补齐任务成功状态。
     *
     * @param task 当前任务
     */
    private void markSucceeded(JudgeTask task) {
        int affectedRows = judgeTaskMapper.update(
                Wrappers.<JudgeTask>lambdaUpdate()
                        .set(JudgeTask::getStatus, JudgeTaskStatus.SUCCEEDED)
                        .set(JudgeTask::getLeaseOwner, null)
                        .set(JudgeTask::getLeaseExpireTime, null)
                        .set(JudgeTask::getNextRetryTime, null)
                        .set(JudgeTask::getLastError, null)
                        .eq(JudgeTask::getId, task.getId())
                        .eq(JudgeTask::getStatus, JudgeTaskStatus.PROCESSING)
                        .eq(JudgeTask::getLeaseOwner, task.getLeaseOwner())
        );

        if (affectedRows != 1) {
            log.info("业务已终态但任务成功状态未补齐，可能已经被其他线程处理，taskId={}", task.getId());
        }
    }


    /**
     * 在测评任务死亡后写入业务记录的系统错误终态。
     */
    private void markSystemError(String taskType, Long businessId, String errorMessage) {
        JudgeOutcome outcome = new JudgeOutcome();
        outcome.setCurStatus(SystemConstant.SYSTEM_ERROR);
        outcome.setJudgeMsg(errorMessage);

        // 普通测评
        if (JudgeTaskType.SUBMISSION.equals(taskType)) {
            Submission submission = submissionMapper.selectById(businessId);
            if (submission == null) {
                log.error("普通提交不存在，submissionId={}", businessId);
                return;
            }

            submissionResultService.updateSubmission(submission, outcome);
            return;
        }

        // 验题
        if (JudgeTaskType.PROBLEM_REVIEW.equals(taskType)) {
            ProblemReviewSubmission submission = reviewSubmissionMapper.selectById(businessId);
            if (submission == null) {
                log.error("验题提交不存在，submissionId={}", businessId);
                return;
            }
            // 因为是验题, 不需要更新有关用户做题数据的表
            submissionResultService.updateSubmission(submission, outcome);
            return;
        }

        // 比赛提交
        if (JudgeTaskType.CONTEST_SUBMISSION.equals(taskType)) {
            ContestSubmission submission = contestSubmissionMapper.selectById(businessId);
            if (submission == null) {
                log.error("比赛提交不存在，submissionId={}", businessId);
                return;
            }
            submissionResultService.updateSubmission(submission, outcome);
            return;
        }

        log.error("无法写入系统错误，不支持的任务类型：{}", taskType);
    }
}
