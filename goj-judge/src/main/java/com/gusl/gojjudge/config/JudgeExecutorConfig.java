package com.gusl.gojjudge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Judge 后台线程池配置。
 *
 * <p>两个消费者线程分别领取普通提交和管理员验题任务，实际测评统一交给一个单线程
 * 执行器，以保证 MVP 阶段的资源占用可控。后续扩容时应同步考虑提交状态幂等。</p>
 */
@Configuration
public class JudgeExecutorConfig {

    /**
     * 创建 Redis 队列消费者线程池。
     *
     * @return 允许普通提交和管理员验题两个长期运行消费者的线程池
     */
    @Bean
    public ThreadPoolTaskExecutor judgeConsumerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("judge-consumer-");
        return executor;
    }

    /**
     * 创建测评任务执行线程池。
     *
     * @return 执行测评业务的线程池
     */
    @Bean("judgeTaskExecutor")
    public ThreadPoolTaskExecutor judgeTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("judge-task-");
        executor.initialize();
        return executor;
    }
}
