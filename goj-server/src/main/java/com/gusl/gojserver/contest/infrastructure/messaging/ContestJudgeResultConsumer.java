package com.gusl.gojserver.contest.infrastructure.messaging;

import com.gusl.common.pojo.entity.ContestJudgeResultMessage;
import com.gusl.gojserver.contest.application.ranking.RankingManager;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 比赛结果消费者，业务异常交给 RocketMQ 重投，幂等由 Manager 处理。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "goj.contest.ranking", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "${goj.contest.ranking.result-topic}",
        consumerGroup = "${goj.contest.ranking.consumer-group}",
        consumeThreadNumber = 1,
        consumeThreadMax = 1
)
public class ContestJudgeResultConsumer implements RocketMQListener<ContestJudgeResultMessage> {

    private final RankingManager rankingManager;

    /** 转交当前这一份提交的结果。 */
    @Override
    public void onMessage(ContestJudgeResultMessage message) {
        rankingManager.applyResult(message);
    }
}
