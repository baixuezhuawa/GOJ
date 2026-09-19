package com.gusl.gojserver.judgetask.infrastructure.messaging;

import com.gusl.common.pojo.entity.JudgeTaskMessage;
import com.gusl.gojserver.judgetask.application.port.JudgeTaskPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 发送任务到消息到 RocketMQ
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RocketMqJudgeTaskPublisher implements JudgeTaskPublisher {

    private final RocketMQTemplate rocketMQTemplate;

    @Value("${goj.judge.rocketmq.topic}")
    private String topic;

    @Override
    public void publish(JudgeTaskMessage message) {
        Message<JudgeTaskMessage> rocketMessage = MessageBuilder
                .withPayload(message)
                .setHeader(RocketMQHeaders.KEYS, String.valueOf(message.getTaskId()))
                .setHeader(RocketMQHeaders.TAGS, message.getTaskType())
                .build();

        // 同步发送消息
        rocketMQTemplate.syncSend(topic + ":" + message.getTaskType(), rocketMessage);

        log.info("判题任务发送成功, taskId={}, taskType={}", message.getTaskId(), message.getTaskType());
    }
}
