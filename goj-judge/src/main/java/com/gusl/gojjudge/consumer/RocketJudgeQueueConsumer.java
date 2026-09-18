package com.gusl.gojjudge.consumer;

import com.gusl.common.pojo.entity.JudgeTaskMessage;
import com.gusl.gojjudge.sercice.application.JudgeTaskProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Rocket 判题任务消费者
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "goj.judge.rocketmq",
        name = "enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "${goj.judge.rocketmq.topic}",
        consumerGroup = "${goj.judge.rocketmq.consumer-group}",
        selectorExpression = "*",
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        consumeThreadMax = 1,
        consumeThreadNumber = 1,
        maxReconsumeTimes = 0,
        consumeTimeout = 15L
)
public class RocketJudgeQueueConsumer implements RocketMQListener<JudgeTaskMessage> {

    private final JudgeTaskProcessor judgeTaskProcessor;

    @Override
    public void onMessage(JudgeTaskMessage message) {
        log.info("判题任务, taskId={}, taskType={}", message.getTaskId(), message.getTaskType());
        judgeTaskProcessor.process(message);
    }
}
