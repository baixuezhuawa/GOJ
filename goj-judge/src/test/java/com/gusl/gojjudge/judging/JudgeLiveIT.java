package com.gusl.gojjudge.judging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gusl.gojjudge.GojJudgeApplication;
import com.gusl.common.constant.JudgeQueueConstant;
import com.gusl.gojjudge.task.JudgeTaskStateService;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.client.impl.MQClientAPIImpl;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 显式运行的真实链路测试：独立数据库和 MQ topic，结束后清理测试数据库。
 * 执行 mvn -pl goj-judge -am -Dtest=JudgeLiveIT -Dsurefire.failIfNoSpecifiedTests=false test。
 * 需要本地 MySQL、Redis 及配置中的 RocketMQ、go-judge；不由默认单元测试自动执行。
 */
class JudgeLiveIT {
    @TempDir
    Path dataRoot;

    private JdbcTemplate jdbc;
    private DefaultMQProducer producer;
    private String topic;
    private final boolean redisMode = Boolean.getBoolean("live.redis");
    private StringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper();

    /** 验证真实消费、沙箱执行、业务回写、重试及恢复，业务数据始终位于临时库。 */
    @Test
    void realQueueSandboxAndDatabase() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String database = "goj_judge_it_" + suffix;
        topic = "GOJ_JUDGE_IT_" + suffix;
        String password = System.getenv("GOJ_MYSQL_PASSWORD");
        assertNotNull(password, "需要配置 GOJ_MYSQL_PASSWORD");
        ConfigurableApplicationContext context = null;
        LettuceConnectionFactory redisConnection = null;
        boolean created = false;
        try (Connection admin = DriverManager.getConnection("jdbc:mysql://localhost:3306/", "root", password);
             Statement statement = admin.createStatement()) {
            try {
                statement.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4");
                created = true;
                // 只复制现有表结构，不复制用户数据，也不执行仓库中的删表脚本。
                for (String table : List.of("problem", "problem_checker", "problem_test_data", "submission",
                        "problem_review_submission", "contest_submission", "contest_problem", "judge_task",
                        "user_problem_progress", "user_activity_day", "user_language_stat", "user_submission_status_stat")) {
                    statement.execute("CREATE TABLE `" + database + "`.`" + table + "` LIKE `goj`.`" + table + "`");
                }
                String url = "jdbc:mysql://localhost:3306/" + database + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
                jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "root", password));
                prepareData();
                if (redisMode) {
                    // 使用空的测试逻辑库，不读取或清空默认库中的业务队列。
                    redisConnection = new LettuceConnectionFactory("localhost", 6379);
                    redisConnection.setDatabase(15);
                    redisConnection.afterPropertiesSet();
                    redisConnection.start();
                    redis = new StringRedisTemplate(redisConnection);
                    assertTrue(redis.keys("*").isEmpty(), "Redis 15 号库非空，拒绝启动测试消费者");
                } else {
                producer = new DefaultMQProducer("judge-it-producer-" + suffix);
                producer.setNamesrvAddr("192.168.100.128:9876");
                producer.setSendMsgTimeout(10000);
                producer.start();
                // 使用已有业务 topic 定位 broker；部分环境关闭了 TBW102 自动建主题。
                producer.createTopic("GOJ_JUDGE_TASK", topic, 1, Map.of());
                SubscriptionGroupConfig group = new SubscriptionGroupConfig();
                group.setGroupName("judge-it-" + suffix);
                producer.getDefaultMQProducerImpl().getmQClientFactory().getMQClientAPIImpl()
                        .createSubscriptionGroup("192.168.100.128:10911", group, 5000);
                }
                context = SpringApplication.run(GojJudgeApplication.class,
                        "--server.port=0", "--spring.datasource.url=" + url,
                        "--goj.judge.data-root=" + dataRoot,
                        "--goj.judge.rocketmq.topic=" + topic,
                        "--goj.judge.rocketmq.consumer-group=judge-it-" + suffix,
                        "--goj.judge.worker-name=judge-it-" + suffix,
                        "--goj.judge.rocketmq.enabled=" + !redisMode,
                        "--goj.judge.redis-consumer-enabled=" + redisMode,
                        "--spring.data.redis.database=15",
                        "--goj.judge.task.retry-initial-delay-seconds=1",
                        "--goj.judge.task.recovery-scan-ms=3600000",
                        "--logging.level.com.gusl=INFO");

                runCase(1, "SUBMISSION", 1, "py3", "print(sum(map(int,input().split())))", "Accepted", 1);
                runCase(2, "SUBMISSION", 1, "py3", "print(99)", "Wrong Answer", 1);
                runCase(3, "SUBMISSION", 1, "py3", "raise RuntimeError('test')", "Runtime Error", 1);
                runCase(4, "SUBMISSION", 1, "java11", "public class Main { broken syntax }", "Compile Error", 1);
                runCase(5, "SUBMISSION", 1, "java11", "public class Main { public static void main(String[] a) { System.out.println(3); } }", "Accepted", 1);
                runCase(6, "SUBMISSION", 1, "py3", "while True: pass", "Time Limit Exceeded", 1);
                runCase(7, "PROBLEM_REVIEW", 2, "py3", "print('3 1 2')", "Accepted", 1);
                runCase(8, "PROBLEM_REVIEW", 2, "py3", "print('3 1 1')", "Wrong Answer", 1);
                runCase(9, "CONTEST_SUBMISSION", 1, "py3", "print(sum(map(int,input().split())))", "Accepted", 1);
                runCase(10, "PROBLEM_REVIEW", 3, "py3", "print('3 1 2')", "system error", 2);

                // 重复投递不能再领取、再执行或重复累计普通提交统计。
                long statsBefore = jdbc.queryForObject("SELECT SUM(submission_count) FROM user_language_stat", Long.class);
                send(1, "SUBMISSION");
                Thread.sleep(1500);
                assertEquals(1, jdbc.queryForObject("SELECT attempt_count FROM judge_task WHERE id=1", Integer.class));
                assertEquals(statsBefore, jdbc.queryForObject("SELECT SUM(submission_count) FROM user_language_stat", Long.class));
                assertEquals(6L, statsBefore);

                // 模拟业务已落库但任务成功写入中断，恢复只补齐任务状态。
                jdbc.update("UPDATE judge_task SET status='PROCESSING',lease_owner='stopped-worker',lease_expire_time=DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE id=1");
                context.getBean(JudgeTaskStateService.class).recover(1L);
                assertEquals("SUCCEEDED", jdbc.queryForObject("SELECT status FROM judge_task WHERE id=1", String.class));
                assertEquals(statsBefore, jdbc.queryForObject("SELECT SUM(submission_count) FROM user_language_stat", Long.class));
                System.out.println("LIVE_PASS duplicate_delivery_and_terminal_recovery");
            } finally {
                if (context != null) {
                    context.close();
                }
                if (redisConnection != null) {
                    redisConnection.destroy();
                }
                if (producer != null) {
                    try {
                        MQClientAPIImpl api = producer.getDefaultMQProducerImpl().getmQClientFactory().getMQClientAPIImpl();
                        api.deleteSubscriptionGroup("192.168.100.128:10911", "judge-it-" + suffix, true, 5000);
                        for (String ownedTopic : List.of(topic, "%RETRY%judge-it-" + suffix)) {
                            api.deleteTopicInBroker("192.168.100.128:10911", ownedTopic, 5000);
                            api.deleteTopicInNameServer("192.168.100.128:9876", ownedTopic, 5000);
                        }
                        System.out.println("LIVE_CLEANUP mq=" + suffix);
                    } catch (Exception cleanupError) {
                        System.out.println("LIVE_CLEANUP_MQ_FAILED " + cleanupError.getMessage());
                    } finally {
                        producer.shutdown();
                    }
                }
                // 名称由本测试生成，只清理已经成功创建的独立数据库。
                if (created && database.matches("goj_judge_it_[a-f0-9]{32}")) {
                    statement.execute("DROP DATABASE `" + database + "`");
                    System.out.println("LIVE_CLEANUP database=" + database + " topic=" + topic);
                }
            }
        }
    }

    /** 准备普通题、特殊题和故障校验程序；验题使用未启用的数据集。 */
    private void prepareData() throws Exception {
        for (int id = 1; id <= 3; id++) {
            jdbc.update("INSERT INTO problem (id,problem_name,problem_type,time_limit,memory_limit,description,input_description,output_description,author_id,status) VALUES (?,?,?,1000,262144,'test','test','test',1,0)",
                    id, "judge-it-" + id, id == 1 ? 0 : 1);
            jdbc.update("INSERT INTO problem_test_data (id,problem_id,version,test_node_count,archive_name,storage_path,active) VALUES (?,?,1,1,'test.zip',?,?)",
                    id, id, "p" + id, id == 1 ? 1 : 0);
            Path dir = Files.createDirectories(dataRoot.resolve("p" + id + "/test1"));
            Files.writeString(dir.resolve("input.txt"), "1 2\n");
            Files.writeString(dir.resolve("output.txt"), id == 1 ? "3\n" : "1 2 3\n");
        }
        jdbc.update("INSERT INTO problem_checker (problem_id,language,source_code) VALUES (2,'py3',?)", "print(' '.join(map(str,sorted(map(int,input().split())))))");
        jdbc.update("INSERT INTO problem_checker (problem_id,language,source_code) VALUES (3,'py3',?)", "raise RuntimeError('checker failed')");
        jdbc.update("INSERT INTO contest_problem (contest_id,problem_id,test_data_id,display_name,display_code,release_status,sort_order) VALUES (1,1,1,'test','A','OPENING',1)");
    }

    /** 投递任务并轮询数据库，系统故障等待到重试时间后再次通过真实 MQ 投递。 */
    private void runCase(long id, String type, long problem, String language, String source, String expected, int attempts) throws Exception {
        String table = table(type);
        String columns = type.equals("PROBLEM_REVIEW") ? "reviewer_id,problem_test_data_id" : type.equals("CONTEST_SUBMISSION") ? "user_id,contest_id,version" : "user_id";
        String values = type.equals("PROBLEM_REVIEW") ? "1," + problem : type.equals("CONTEST_SUBMISSION") ? "1,1,1" : "1";
        jdbc.update("INSERT INTO " + table + " (id,problem_id,language,source_code,status,source_sha256," + columns + ") VALUES (?,?,?,?,'in queue',REPEAT('0',64)," + values + ")", id, problem, language, source);
        jdbc.update("INSERT INTO judge_task (id,task_type,business_id,task_version,status,max_attempts) VALUES (?,?,?,1,'PENDING',?)", id, type, id, attempts);
        send(id, type);
        long deadline = System.nanoTime() + 90_000_000_000L;
        int retriedAttempt = 0;
        while (System.nanoTime() < deadline) {
            Map<String, Object> task = jdbc.queryForMap("SELECT status,attempt_count,last_error,next_retry_time <= NOW() AS due FROM judge_task WHERE id=?", id);
            String status = task.get("status").toString();
            int count = ((Number) task.get("attempt_count")).intValue();
            if (status.equals("SUCCEEDED") || status.equals("DEAD")) {
                Map<String, Object> result = jdbc.queryForMap("SELECT status,judge_msg,compiler_msg,judge_end_time FROM " + table + " WHERE id=?", id);
                System.out.println("LIVE_RESULT " + id + " " + type + " " + language + " " + result + " task=" + task);
                assertEquals(expected, result.get("status"));
                assertEquals(expected.equals("system error") ? "DEAD" : "SUCCEEDED", status);
                assertEquals(attempts, count);
                assertNotNull(result.get("judge_end_time"));
                return;
            }
            if (status.equals("RETRY_WAIT") && count > retriedAttempt && task.get("due") != null
                    && ((Number) task.get("due")).intValue() == 1) {
                retriedAttempt = count;
                send(id, type);
            }
            Thread.sleep(200);
        }
        fail("任务未在期限内结束：" + id + " " + jdbc.queryForMap("SELECT * FROM judge_task WHERE id=?", id));
    }

    /** 使用与正式消费者相同的 JSON 消息字段，通过 RocketMQ 发送。 */
    private void send(long id, String type) throws Exception {
        byte[] body = json.writeValueAsString(Map.of("schemaVersion", 1, "taskId", id, "taskType", type,
                "businessId", id, "taskVersion", 1)).getBytes(StandardCharsets.UTF_8);
        if (redisMode) {
            String queue = type.equals("SUBMISSION") ? JudgeQueueConstant.SUBMISSION_READY_QUEUE
                    : type.equals("CONTEST_SUBMISSION") ? JudgeQueueConstant.CONTEST_READY_QUEUE
                    : JudgeQueueConstant.PROBLEM_REVIEW_READY_QUEUE;
            redis.opsForList().leftPush(queue, new String(body, StandardCharsets.UTF_8));
        } else {
            producer.send(new Message(topic, body));
        }
    }

    /** 表名仅来自测试内部的固定类型，不能由外部输入拼接。 */
    private String table(String type) {
        if (type.equals("SUBMISSION")) return "submission";
        if (type.equals("CONTEST_SUBMISSION")) return "contest_submission";
        return "problem_review_submission";
    }
}
