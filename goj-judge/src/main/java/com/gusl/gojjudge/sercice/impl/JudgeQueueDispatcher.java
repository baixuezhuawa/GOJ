package com.gusl.gojjudge.sercice.impl;

import com.alibaba.fastjson2.JSON;
import com.gusl.common.constant.JudgeQueueConstant;
import com.gusl.common.pojo.entity.JudgeTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 任务调度器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeQueueDispatcher {

    /** Redis 字符串模板，用于阻塞式领取队列中的提交 ID。 */
    private final StringRedisTemplate stringRedisTemplate;

    /** 领取任务, 开启判题 */
    private final JudgeTaskProcessor processor;

    /**
     * 任务类型队列
     */
    private static final List<String> thisQueue = new ArrayList<>();
    // 初始化任务队列
    static {
        int baseCount = 1;
        int contest = 5;
        int regular = 1;
        int admin = 1;
        for(int i = 0; i < contest * baseCount; i++){
            thisQueue.add(JudgeQueueConstant.CONTEST_READY_QUEUE);
        }
        for(int i = 0; i < regular * baseCount; i++){
            thisQueue.add(JudgeQueueConstant.SUBMISSION_READY_QUEUE);
        }
        for(int i = 0; i < admin * baseCount; i++){
            thisQueue.add(JudgeQueueConstant.PROBLEM_REVIEW_READY_QUEUE);
        }
        Collections.shuffle(thisQueue);
    }

    /** 指针 */
    private int point = 0;

    /** 每个槽位最多停留时间 */
    @Value("${goj.judge.dispatch.slot-max-wait-time-ms:50}")
    private long slotMaxWaitTimeMs;

    /** 每个槽位的扫描间隔 */
    @Value("${goj.judge.dispatch.scan-interval:2}")
    private long scanInterval;



    /** 任务调度 */
    public void dispatcher(){

        // Redis异常的等待时间
        final long redisErrorBackoffTimeMs = 1000L;

        while(!Thread.currentThread().isInterrupted()){
            String queue = thisQueue.get(point);
            String payload = null;
            boolean redisError = false;

            // 根据当前指针所在槽位的队列领取任务
            try{
                payload = pollSlot(queue);
            }catch (Exception e){
                redisError = true;
                log.error("Judge 任务调度失败, queue = {}", queue, e);
            }

            // 不管成功与否都移动指针
            movePoint();

            // 防止Redis异常, 导致高速重试
            if(redisError && sleepQuietly(redisErrorBackoffTimeMs)){
                break;
            }

            if (payload == null){
                continue;
            }

            try {
                // 解析消息并执行测评。
                JudgeTaskMessage message = JSON.parseObject(payload, JudgeTaskMessage.class);

                processor.process(message);

            } catch (Exception exception) {
                // 单个消息异常不能导致整个调度器退出。
                log.error("Judge 任务处理失败，queue={}", queue, exception);
            }

        }

        log.info("Judge 任务调度器已停止");
    }



    /** 移动指针 */
    private void movePoint(){
        point = (point + 1) % thisQueue.size();
    }



    /**
     * 在指定队列上等待一个槽位时间
     * @param queue 队列
     * @return payload
     */
    private String pollSlot(String queue){
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(slotMaxWaitTimeMs);

        while(!Thread.currentThread().isInterrupted() && System.nanoTime() < deadline){
            String payload = stringRedisTemplate.opsForList().rightPop(queue);

            if(payload != null){
                return payload;
            }

            // 当前队列没有任务, 根据间隔时间后再次检查.
            if(sleepQuietly(scanInterval)){
                return null;
            }

        }

        // 已经超时
        return null;
    }



    /**
     * 延时等待，并正确处理中断信号。
     * @param sleepTimeMs 睡眠时间
     * @return 如果睡眠异常 true, 正常 false
     */
    private boolean sleepQuietly(long sleepTimeMs) {
        try {
            Thread.sleep(sleepTimeMs);
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return true;
        }
    }
}
