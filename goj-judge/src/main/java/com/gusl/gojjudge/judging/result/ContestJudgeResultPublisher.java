package com.gusl.gojjudge.judging.result;

import com.gusl.common.pojo.entity.ContestJudgeResultMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 比赛结果发送器，在结果事务提交后发送当前提交的最终状态。 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "goj.contest.ranking", name = "enabled", havingValue = "true")
public class ContestJudgeResultPublisher {
    private final RocketMQTemplate rocketMQ;
    @Value("${goj.contest.ranking.result-topic}")
    private String topic;

    /** 数据提交成功才通知榜单；发送失败记录错误，不把已提交的判题误报为失败。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ContestJudgeResultMessage result) {
        try {
            rocketMQ.syncSend(topic, MessageBuilder.withPayload(result)
                    .setHeader(RocketMQHeaders.KEYS, result.getSubmissionId() + ":" + result.getVersion()).build());
        } catch (Exception exception) {
            log.error("比赛结果通知发送失败，实时榜可能缺少该结果，contestId={}, submissionId={}",
                    result.getContestId(), result.getSubmissionId(), exception);
        }
    }
}
