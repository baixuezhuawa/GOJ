package com.gusl.gojjudge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class JudgeExecutorConfig {

    /**
     * 消费线程, 监听Redis并领取测评任务
     * @return 线程
     */
    @Bean
    public ThreadPoolTaskExecutor judgeConsumerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("judge-consumer-");
        return executor;
    }

    /**
     * 任务线程, 执行测评任务
     * @return 线程
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