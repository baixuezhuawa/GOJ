package com.gusl.gojserver.contest.infrastructure.conf;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ContestRankExecutorConfig {

    /**
     * 比赛排行榜线程池
     */
    @Bean("contestRankExecutor")
    public ThreadPoolTaskExecutor contestRankExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 初始化线程数量
        executor.setCorePoolSize(3);

        // 最大线程数量
        executor.setMaxPoolSize(5);

        // 定位日志
        executor.setThreadNamePrefix("contest-rank-");

        // 队列满时不要静默丢弃任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.setQueueCapacity(1000);

        // 应用关闭是等待任务结束
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        return executor;
    }
}
