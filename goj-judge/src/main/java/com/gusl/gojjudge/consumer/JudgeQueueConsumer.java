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

/**
 * Judge Redis 队列消费者。
 *
 * <p>应用启动完成后创建一个后台消费循环，从共享队列中阻塞式领取提交 ID，
 * 再交给 {@link JudgeService} 执行测评。队列消息只承担唤醒和传递 ID 的职责，
 * 提交、题目、源码和测试数据仍从可信存储重新加载。</p>
 */
@Slf4j
@Component
@AllArgsConstructor
public class JudgeQueueConsumer {

    /** Redis 字符串模板，用于阻塞式领取队列中的提交 ID。 */
    private final StringRedisTemplate stringRedisTemplate;

    /** 测评业务服务，负责校验任务并执行完整测评。 */
    private final JudgeService judgeService;

    /** 专用于长期运行消费循环的线程执行器。 */
    private final TaskExecutor judgeConsumerExecutor;

    /**
     * 监听 Spring 启动完成事件，避免应用尚未建立数据库和 Redis 连接时消费任务。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        judgeConsumerExecutor.execute(this::consume);
    }

    /**
     * 持续消费测评队列。
     *
     * <p>每次最多阻塞 3 秒，超时后重新检查线程中断标记；取到任务后调用业务服务，
     * 由业务服务负责幂等状态检查和完整测评流程。</p>
     */
    private void consume() {
        while (!Thread.currentThread().isInterrupted()) {
            // rightPop 与 server 的 leftPush 配合形成 FIFO 队列；超时便于优雅停止。
            String taskId = stringRedisTemplate.opsForList().rightPop(
                    JudgeQueueConstant.READY_QUEUE, 3, TimeUnit.SECONDS
            );
            if (taskId == null) {
                continue;
            }
            try {
                judgeService.judge(Long.valueOf(taskId));
            } catch (Exception e) {
                log.error("Redis 获取判题任务或执行判题失败", e);
            }
        }
    }
}
