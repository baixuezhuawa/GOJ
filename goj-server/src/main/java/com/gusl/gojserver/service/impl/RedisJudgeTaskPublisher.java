package com.gusl.gojserver.service.impl;

import com.alibaba.fastjson2.JSON;
import com.gusl.common.constant.JudgeQueueConstant;
import com.gusl.common.constant.JudgeTaskType;
import com.gusl.common.pojo.entity.JudgeTaskMessage;
import com.gusl.gojserver.service.JudgeTaskPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 发布任务到 Redis 队列
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisJudgeTaskPublisher implements JudgeTaskPublisher {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 将测评任务消息发布到对应的 Redis 队列。
     *
     * @param message 测评任务消息
     */
    @Override
    public void publish(JudgeTaskMessage message) {
        // 获取需要发送的队列
        String queue = getQueueName(message.getTaskType());

        // 转换为JSON字符串
        String payload = JSON.toJSONString(message);

        // 发送
        stringRedisTemplate.opsForList().leftPush(queue, payload);

        log.info("测评任务发送成功, taskId:{} taskType:{}  businessId:{} ",
                message.getTaskId(),
                message.getTaskType(),
                message.getBusinessId()
        );
    }


    /**
     * 根据任务类型获取对应的 Redis 队列名称。
     *
     * @param taskType 任务类型
     * @return Redis 队列名称
     */
    private String getQueueName(String taskType) {
        if (JudgeTaskType.SUBMISSION.equals(taskType)) {
            return JudgeQueueConstant.SUBMISSION_READY_QUEUE;
        }

        if (JudgeTaskType.PROBLEM_REVIEW.equals(taskType)) {
            return JudgeQueueConstant.PROBLEM_REVIEW_READY_QUEUE;
        }

        throw new IllegalArgumentException("不支持的测评任务类型：" + taskType);
    }
}
