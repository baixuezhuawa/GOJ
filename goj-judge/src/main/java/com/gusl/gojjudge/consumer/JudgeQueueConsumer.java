package com.gusl.gojjudge.consumer;


import com.gusl.gojjudge.sercice.JudgeService;
import com.gusl.gojjudge.sercice.impl.JudgeQueueDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

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



    /** 专用于两个长期运行消费循环的线程执行器。 */
    private final TaskExecutor judgeConsumerExecutor;

    /** 任务调度器 */
    private final JudgeQueueDispatcher judgeQueueDispatcher;


    /** 构造函数 */
    public JudgeQueueConsumer(
            JudgeQueueDispatcher judgeQueueDispatcher,
            @Qualifier("judgeConsumerExecutor") TaskExecutor judgeConsumerExecutor
    ) {
        this.judgeQueueDispatcher = judgeQueueDispatcher;
        this.judgeConsumerExecutor = judgeConsumerExecutor;
    }


    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        judgeConsumerExecutor.execute(judgeQueueDispatcher::dispatcher);
    }
}
