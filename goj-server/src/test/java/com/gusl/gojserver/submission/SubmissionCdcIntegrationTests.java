package com.gusl.gojserver.submission;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.constant.SubmissionType;
import com.gusl.gojserver.shared.infrastructure.config.properties.SubmissionCacheProperties;
import com.gusl.gojserver.submission.application.SubmissionStatusService;
import com.gusl.gojserver.submission.application.model.SubmissionStatusSnapshot;
import com.gusl.gojserver.submission.infrastructure.cache.SubmissionStatusCache;
import com.gusl.gojserver.submission.infrastructure.canal.SubmissionCanalConsumer;
import com.gusl.gojserver.submission.infrastructure.canal.SubmissionSyncHealth;
import com.gusl.gojserver.submission.infrastructure.persistence.SubmissionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 MySQL、独立 Canal 进程和 Redis 集成验证，不写入业务库。 */
@EnabledIfEnvironmentVariable(named = "GOJ_TEST_CDC", matches = "true")
class SubmissionCdcIntegrationTests {

    /** 验证增量、终态期限、重放、重判、断线回源及重连恢复。 */
    @Test
    void mysqlCanalRedisLifecycle() throws Exception {
        String database = "goj_cdc_test_" + UUID.randomUUID().toString().replace("-", "");
        String password = System.getenv("GOJ_MYSQL_PASSWORD");
        String server = System.getenv().getOrDefault("GOJ_TEST_MYSQL_SERVER_URL", "jdbc:mysql://127.0.0.1:3306/");
        String canalDirectory = System.getenv("GOJ_CANAL_HOME");
        assertTrue(canalDirectory != null && !canalDirectory.isBlank(), "请设置 GOJ_CANAL_HOME 环境变量为 Canal 安装目录");
        Path canalHome = Path.of(canalDirectory);
        Path work = Path.of("target", database).toAbsolutePath();
        Files.createDirectories(work.resolve("conf/cdc_test"));
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(server, "root", password));
        LettuceConnectionFactory connection = new LettuceConnectionFactory("127.0.0.1", 6379);
        connection.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(connection);
        // 负数测试 ID 与业务自增 ID 隔离，清理时仅删除这个测试键。
        long id = -Math.abs(System.nanoTime());
        String key = "goj:submission:result:" + id;
        Process process = null;
        SubmissionCanalConsumer consumer = null;
        boolean granted = false;
        Properties instance = load(canalHome.resolve("conf/goj/instance.properties"));
        String account = instance.getProperty("canal.instance.dbUsername");
        if (!"canal".equals(account)) throw new IllegalStateException("集成测试要求本机 canal 专用账号");
        try {
            admin.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4");
            admin.execute("GRANT SELECT ON " + database + ".* TO 'canal'@'127.0.0.1'");
            granted = true;
            DriverManagerDataSource dataSource = new DriverManagerDataSource(server + database, "root", password);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Path root = Path.of(System.getProperty("user.dir")).getParent();
            Matcher ddl = Pattern.compile("CREATE TABLE `submission`[\\s\\S]*?;").matcher(Files.readString(root.resolve("sql/schema.sql")));
            assertTrue(ddl.find());
            jdbc.execute(ddl.group());

            // 创建完全独立的实例、端口、位点和日志，不改已安装 Canal 的配置。
            Properties serverConfig = load(canalHome.resolve("conf/canal.properties"));
            serverConfig.setProperty("canal.port", "11121");
            serverConfig.setProperty("canal.admin.port", "11120");
            serverConfig.setProperty("canal.metrics.pull.port", "11122");
            serverConfig.setProperty("canal.destinations", "cdc_test");
            serverConfig.setProperty("canal.auto.scan", "false");
            serverConfig.setProperty("canal.conf.dir", work.resolve("conf").toString().replace('\\', '/'));
            serverConfig.setProperty("canal.file.data.dir", work.resolve("conf").toString().replace('\\', '/'));
            serverConfig.setProperty("canal.instance.global.spring.xml", "classpath:spring/file-instance.xml");
            save(serverConfig, work.resolve("conf/canal.properties"));
            instance.setProperty("canal.instance.mysql.slaveId", "190026");
            instance.setProperty("canal.instance.filter.regex", database + "\\.submission");
            instance.setProperty("canal.instance.master.journal.name", "");
            instance.setProperty("canal.instance.master.position", "");
            instance.setProperty("canal.instance.master.timestamp", "");
            instance.setProperty("canal.instance.tsdb.url", "jdbc:h2:" + work.resolve("tsdb").toString().replace('\\', '/') + ";CACHE_SIZE=1000;MODE=MYSQL;");
            save(instance, work.resolve("conf/cdc_test/instance.properties"));
            ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java.exe").toString(),
                    "-Xms128m", "-Xmx512m", "-Dfile.encoding=UTF-8",
                    "-Dcanal.conf=" + work.resolve("conf/canal.properties"),
                    "-Dlogback.configurationFile=" + canalHome.resolve("conf/logback.xml"),
                    "-cp", work.resolve("conf") + ";" + canalHome.resolve("conf") + ";" + canalHome.resolve("lib") + "/*",
                    "com.alibaba.otter.canal.deployer.CanalLauncher");
            builder.directory(work.toFile()).redirectErrorStream(true).redirectOutput(work.resolve("console.log").toFile());
            process = builder.start();
            await(() -> portOpen(11121), 30);

            MybatisConfiguration config = new MybatisConfiguration();
            config.setMapUnderscoreToCamelCase(true);
            config.addMapper(SubmissionMapper.class);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setConfiguration(config);
            SubmissionMapper mapper = new SqlSessionTemplate(factory.getObject()).getMapper(SubmissionMapper.class);
            SubmissionCacheProperties properties = new SubmissionCacheProperties();
            properties.setEnabled(true);
            properties.setPort(11121);
            properties.setDestination("cdc_test");
            properties.setDatabase(database);
            SubmissionSyncHealth health = new SubmissionSyncHealth(properties);
            SubmissionStatusCache cache = new SubmissionStatusCache(redis, new ObjectMapper().findAndRegisterModules(), properties);
            SubmissionStatusService service = new SubmissionStatusService(mapper, cache, properties, health, new DataSourceTransactionManager(dataSource));
            consumer = new SubmissionCanalConsumer(properties, service, health);
            consumer.start();
            await(health::usable, 15);
            // 等待 Canal 首次确定增量起点，再产生测试变更。
            Thread.sleep(2000);
            jdbc.update("INSERT INTO submission(id,user_id,problem_id,language,source_code,source_sha256,status) VALUES(?,1,1,'py3','pass',?,'in queue')", id, "0".repeat(64));
            await(() -> cache.get(id) != null, 20);
            assertEquals("in queue", cache.get(id).getStatus());
            jdbc.update("UPDATE submission SET status='Accepted', time_ms=12, judge_end_time=NOW() WHERE id=?", id);
            await(() -> JudgingConstant.ACCEPTED.equals(cache.get(id).getStatus()), 15);
            long ttl = redis.getExpire(key, TimeUnit.MILLISECONDS);
            assertTrue(ttl > 1190000 && ttl <= 1320000, "终态期限应为 20 分钟加抖动");
            service.synchronize(id);
            assertTrue(redis.getExpire(key, TimeUnit.MILLISECONDS) <= ttl, "重放不续期");
            jdbc.update("UPDATE submission SET status='in queue',version=2,time_ms=NULL,judge_end_time=NULL WHERE id=?", id);
            await(() -> cache.get(id).getVersion() == 2, 15);
            assertNull(cache.get(id).getTimeMs());
            assertNull(cache.get(id).getJudgeEndTime());
            assertTrue(redis.getExpire(key) <= 30);

            consumer.stop();
            jdbc.update("UPDATE submission SET status='Wrong Answer' WHERE id=?", id);
            assertEquals(JudgingConstant.WRONG_ANSWER, service.getStatus(id, SubmissionType.REGULAR, null, false).getStatus());
            consumer.start();
            await(health::usable, 15);
            jdbc.update("DELETE FROM submission WHERE id=?", id);
            await(() -> !Boolean.TRUE.equals(redis.hasKey(key)), 15);
        } finally {
            if (consumer != null) consumer.stop();
            if (process != null) {
                process.destroy();
                if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly();
            }
            redis.delete(key);
            connection.destroy();
            if (granted) admin.execute("REVOKE SELECT ON " + database + ".* FROM 'canal'@'127.0.0.1'");
            admin.execute("DROP DATABASE IF EXISTS " + database);
            // 清除临时复制账号密码，日志和位点保留用于失败排查。
            Files.deleteIfExists(work.resolve("conf/cdc_test/instance.properties"));
        }
    }

    /** 读取安装目录配置，密码仅在内存和临时配置中使用。 */
    private Properties load(Path path) throws Exception {
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(path)) { properties.load(stream); }
        return properties;
    }

    /** 保存独立实例配置。 */
    private void save(Properties properties, Path path) throws Exception {
        try (OutputStream stream = Files.newOutputStream(path)) { properties.store(stream, "CDC integration test"); }
    }

    /** 探测测试端口是否就绪。 */
    private boolean portOpen(int port) {
        try (Socket socket = new Socket("127.0.0.1", port)) { return socket.isConnected(); }
        catch (Exception exception) { return false; }
    }

    /** 有界等待异步状态，失败时保留 Canal 日志供诊断。 */
    private void await(BooleanSupplier condition, int seconds) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(100);
        }
        fail("等待 CDC 状态超时，请检查 target/goj_cdc_test_*/console.log 和 logs");
    }
}
