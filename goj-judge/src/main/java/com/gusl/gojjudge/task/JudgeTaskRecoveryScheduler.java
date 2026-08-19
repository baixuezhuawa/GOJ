package com.gusl.gojjudge.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.constant.JudgeTaskStatus;
import com.gusl.common.pojo.entity.JudgeTask;
import com.gusl.gojjudge.mapper.JudgeTaskMapper;
import com.gusl.gojjudge.sercice.impl.JudgeTaskRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 测评任务租约恢复定时器，批量扫描超过租约时间的 PROCESSING 任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeTaskRecoveryScheduler {

    private final JudgeTaskMapper judgeTaskMapper;

    private final JudgeTaskRecoveryService recoveryService;

    /**
     * 定时扫描租约过期且处于 PROCESSING 的任务。
     */
    @Scheduled(fixedDelayString = "${goj.judge.task.recovery-scan-ms:30000}")
    public void scanExpiredTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<JudgeTask> judgeTasks = judgeTaskMapper.selectList(
                Wrappers.<JudgeTask>lambdaQuery()
                        .eq(JudgeTask::getStatus, JudgeTaskStatus.PROCESSING)
                        .le(JudgeTask::getLeaseExpireTime, now)
                        .orderByAsc(JudgeTask::getId)
                        .last("LIMIT 100")
        );

        for (JudgeTask judgeTask : judgeTasks) {
            try {
                // 每条任务通过独立 Service 调用开启事务，避免单条失败影响整个批次。
                recoveryService.recover(judgeTask.getId());
            } catch (Exception exception) {
                log.error("恢复租约过期任务失败，taskId={}", judgeTask.getId(), exception);
            }
        }
    }

}
