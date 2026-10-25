package com.gusl.gojjudge.judging;

import com.gusl.common.pojo.entity.ContestJudgeResultMessage;
import com.gusl.gojjudge.judging.result.ContestJudgeResultPublisher;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 使用 Spring 事务事件验证提交后发送，MQ 为 Mock，不连接 Broker。 */
class ContestResultPublisherTests {
    @Configuration
    @EnableTransactionManagement
    static class Config {
        /** 提供可验证的 MQ 发送端。 */
        @Bean
        RocketMQTemplate rocketMQ() { return mock(RocketMQTemplate.class); }
        /** 注入测试 topic，不依赖外部配置。 */
        @Bean
        ContestJudgeResultPublisher publisher(RocketMQTemplate rocketMQ) {
            ContestJudgeResultPublisher publisher = new ContestJudgeResultPublisher(rocketMQ);
            ReflectionTestUtils.setField(publisher, "topic", "GOJ_CONTEST_RESULT");
            return publisher;
        }
    }

    /** 仅触发事务同步回调，无真实数据库事务。 */
    private static class TestTransactions extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }

    /** 启动最小事件上下文，不启动 Judge 应用及真实 MQ。 */
    private AnnotationConfigApplicationContext context() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "测试配置", java.util.Collections.singletonMap("goj.contest.ranking.result-topic", "GOJ_CONTEST_RESULT")));
        context.register(Config.class);
        context.refresh();
        clearInvocations(context.getBean(RocketMQTemplate.class));
        return context;
    }

    @Test
    void sendsOnlyAfterCommitAndDoesNotSendOnRollback() {
        try (AnnotationConfigApplicationContext context = context()) {
            RocketMQTemplate mq = context.getBean(RocketMQTemplate.class);
            TransactionTemplate transactions = new TransactionTemplate(new TestTransactions());
            transactions.execute(status -> {
                context.publishEvent(new ContestJudgeResultMessage());
                verifyNoInteractions(mq);
                return null;
            });
            verify(mq).syncSend(eq("GOJ_CONTEST_RESULT"), any(org.springframework.messaging.Message.class));
            clearInvocations(mq);
            transactions.execute(status -> {
                context.publishEvent(new ContestJudgeResultMessage());
                status.setRollbackOnly();
                return null;
            });
            verifyNoInteractions(mq);
        }
    }

    @Test
    void sendingFailureDoesNotChangeCommittedJudgingResult() {
        try (AnnotationConfigApplicationContext context = context()) {
            RocketMQTemplate mq = context.getBean(RocketMQTemplate.class);
            when(mq.syncSend(anyString(), any(org.springframework.messaging.Message.class)))
                    .thenThrow(new IllegalStateException("测试发送失败"));
            new TransactionTemplate(new TestTransactions()).execute(status -> {
                context.publishEvent(new ContestJudgeResultMessage());
                return null;
            });
            verify(mq).syncSend(anyString(), any(org.springframework.messaging.Message.class));
        }
    }
}
