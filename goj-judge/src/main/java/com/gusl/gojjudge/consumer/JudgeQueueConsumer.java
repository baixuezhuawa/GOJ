package com.gusl.gojjudge.consumer;


import com.alibaba.fastjson2.JSON;
import com.gusl.common.constant.JudgeQueueConstant;
import com.gusl.common.pojo.entity.JudgeTaskMessage;
import com.gusl.gojjudge.sercice.JudgeService;
import com.gusl.gojjudge.sercice.impl.JudgeTaskProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Judge Redis 队列消费者。
 *
 * <p>应用启动完成后分别创建普通提交和管理员验题两个消费循环，从各自 Redis 队列中
 * 阻塞式领取任务 ID，再交给 {@link JudgeService} 对应入口执行测评。队列消息只承担唤醒
 * 和传递 ID 的职责，提交、题目、源码和测试数据仍从可信存储重新加载。</p>
 */
@Slf4j
@Component
public class JudgeQueueConsumer {

    /** Redis 字符串模板，用于阻塞式领取队列中的提交 ID。 */
    private final StringRedisTemplate stringRedisTemplate;

    /** 专用于两个长期运行消费循环的线程执行器。 */
    private final TaskExecutor judgeConsumerExecutor;

    /** 单线程执行实际测评，避免多个任务同时争抢本机和沙箱资源。 */
    private final TaskExecutor judgeTaskExecutor;

    private final JudgeTaskProcessor processor;


    public JudgeQueueConsumer(
            StringRedisTemplate stringRedisTemplate,
            JudgeService judgeService,
            JudgeTaskProcessor processor,
            @Qualifier("judgeConsumerExecutor") TaskExecutor judgeConsumerExecutor,
            @Qualifier("judgeTaskExecutor") TaskExecutor judgeTaskExecutor
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.processor = processor;
        this.judgeConsumerExecutor = judgeConsumerExecutor;
        this.judgeTaskExecutor = judgeTaskExecutor;
    }

    /**
     * 监听 Spring 启动完成事件，避免应用尚未建立数据库和 Redis 连接时消费任务。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        // 监听不同的队列, 通过 Consumer 类实现不同类型的任务使用不同的任务处理器

        // 普通提交和管理员验题使用独立队列及独立消费循环。
        judgeConsumerExecutor.execute(() -> consume(
                JudgeQueueConstant.SUBMISSION_READY_QUEUE,
                "普通提交"
        ));
        judgeConsumerExecutor.execute(() -> consume(
                JudgeQueueConstant.PROBLEM_REVIEW_READY_QUEUE,
                "管理员验题提交"
        ));
    }

    /**
     * 持续消费测评队列。
     *  <p>每次最多阻塞 3 秒，超时后重新检查线程中断标记；取到任务后调用业务服务，
     *  由业务服务负责幂等状态检查和完整测评流程。</p>
     */
    private void consume(String queueName, String taskType) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // rightPop 与 server 的 leftPush 配合形成 FIFO；超时后可以检查线程中断状态。
                String payload = stringRedisTemplate.opsForList().rightPop(
                        queueName,
                        3,
                        TimeUnit.SECONDS
                );
                if (payload == null) {
                    continue;
                }

                JudgeTaskMessage message = JSON.parseObject(
                        payload,
                        JudgeTaskMessage.class
                );

                // 交给 process
                judgeTaskExecutor.execute(() -> processor.process(message));

            } catch (Exception exception) {
                log.error("{}队列消费失败，queue={}", taskType, queueName, exception);
            }
        }
    }

}
