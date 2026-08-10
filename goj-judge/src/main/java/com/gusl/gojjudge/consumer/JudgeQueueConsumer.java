package com.gusl.gojjudge.consumer;


import com.gusl.common.constant.JudgeQueueConstant;
import com.gusl.gojjudge.sercice.JudgeService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@AllArgsConstructor
public class JudgeQueueConsumer {

    private final StringRedisTemplate stringRedisTemplate;
    private final JudgeService judgeService;
    private final TaskExecutor judgeConsumerExecutor;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        judgeConsumerExecutor.execute(this::consume);
    }

    private void consume() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                String taskId = stringRedisTemplate.opsForList().rightPop(
                        JudgeQueueConstant.READY_QUEUE, 3, TimeUnit.SECONDS
                );
                if (taskId == null) {
                    continue;
                }
                judgeService.judge(Long.valueOf(taskId));
            }
        } catch (IOException e) {
            log.error("Redis 获取判题任务或执行判题失败", e);
        }
    }
}