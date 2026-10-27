package com.gusl.gojserver.submission;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.gusl.common.constant.ContestProblemStatus;
import com.gusl.common.constant.ContestStatus;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojserver.account.infrastructure.persistence.UserMapper;
import com.gusl.gojserver.account.infrastructure.security.LoginUser;
import com.gusl.gojserver.contest.application.ContestSubmissionService;
import com.gusl.gojserver.contest.application.ContestSubmissionServiceImpl;
import com.gusl.gojserver.contest.application.dto.ContestSubmission2JudgeDto;
import com.gusl.gojserver.contest.infrastructure.persistence.*;
import com.gusl.gojserver.judgetask.application.support.JudgeSourceValidator;
import com.gusl.gojserver.judgetask.infrastructure.persistence.JudgeTaskMapper;
import com.gusl.gojserver.problem.infrastructure.persistence.ProblemMapper;
import com.gusl.gojserver.problem.infrastructure.persistence.ProblemTestDataMapper;
import com.gusl.gojserver.shared.application.pagination.PageFactory;
import com.gusl.gojserver.shared.infrastructure.persistence.MyMetaObjectHandler;
import com.gusl.gojserver.shared.infrastructure.config.properties.SysProperties;
import com.gusl.gojserver.shared.infrastructure.config.properties.entity.ProfileProperties;
import com.gusl.gojserver.submission.application.*;
import com.gusl.gojserver.submission.application.dto.Submission2JudgeDto;
import com.gusl.gojserver.submission.application.model.SubmissionRequestIdentity;
import com.gusl.gojserver.submission.application.model.SubmissionRequestException;
import com.gusl.gojserver.submission.application.vo.SubmissionVo;
import com.gusl.gojserver.submission.infrastructure.persistence.SubmissionMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

/**
 * MySQL 集成测试：使用随机独立数据库、真实 Mapper 和 Spring 事务代理验证幂等。
 * 设置 GOJ_TEST_MYSQL_SERVER_URL（例如 jdbc:mysql://localhost:3306/）启用；不连接业务库。
 */
@EnabledIfEnvironmentVariable(named = "GOJ_TEST_MYSQL_SERVER_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubmissionIdempotencyMysqlTests {
    private String database;
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private SubmissionMapper submissions;
    private JudgeTaskMapper tasks;
    private SubmissionIdempotencyService idempotency;
    private JudgeSourceValidator validator;
    private SysProperties properties;
    private LoginUser user;

    /** 从当前 schema 提取两张表，并用正式迁移脚本增加幂等字段。 */
    @BeforeAll
    void createDatabase() throws Exception {
        String server = System.getenv("GOJ_TEST_MYSQL_SERVER_URL");
        String username = System.getenv().getOrDefault("GOJ_TEST_MYSQL_USER", "root");
        String password = System.getenv("GOJ_MYSQL_PASSWORD");
        database = "goj_idempotency_test_" + UUID.randomUUID().toString().replace("-", "");
        admin = new JdbcTemplate(new DriverManagerDataSource(server, username, password));
        admin.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(server + database, username, password);
        jdbc = new JdbcTemplate(dataSource);
        transactions = new DataSourceTransactionManager(dataSource);
        Path root = Path.of(System.getProperty("user.dir"));
        if (!Files.exists(root.resolve("sql/schema.sql"))) root = root.getParent();
        String schema = Files.readString(root.resolve("sql/schema.sql"));
        for (String table : new String[]{"submission", "judge_task"}) {
            Matcher matcher = Pattern.compile("CREATE TABLE `" + table + "`[\\s\\S]*?;").matcher(schema);
            assertTrue(matcher.find());
            String ddl = matcher.group().replaceAll("(?m)^.*`(?:request_id|request_sha256|uk_submission_user_request)`.*\\R", "");
            jdbc.execute(ddl);
        }
        String migration = Files.readString(root.resolve("sql/migrations/20261026_submission_idempotency.sql"));
        jdbc.execute(migration.replaceAll("(?m)^--.*$", ""));

        // MyBatis-Plus 工厂注入真实 CRUD SQL，SqlSessionTemplate 参与线程绑定事务。
        MybatisConfiguration config = new MybatisConfiguration();
        GlobalConfig global = new GlobalConfig();
        global.setMetaObjectHandler(new MyMetaObjectHandler(null));
        global.setDbConfig(new GlobalConfig.DbConfig());
        GlobalConfigUtils.setGlobalConfig(config, global);
        config.addMapper(SubmissionMapper.class);
        config.addMapper(JudgeTaskMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(config);
        factory.setGlobalConfig(global);
        SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());
        submissions = session.getMapper(SubmissionMapper.class);
        tasks = session.getMapper(JudgeTaskMapper.class);
        idempotency = proxy(new SubmissionIdempotencyService(submissions), SubmissionIdempotencyService.class);
    }

    /** 仅清理本测试创建的随机数据库。 */
    @AfterAll
    void dropDatabase() {
        if (admin != null && database != null && database.matches("goj_idempotency_test_[0-9a-f]{32}")) {
            admin.execute("DROP DATABASE " + database);
        }
    }

    /** 重置测试表与不参与本测试的业务依赖。 */
    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM judge_task");
        jdbc.update("DELETE FROM submission");
        validator = mock(JudgeSourceValidator.class);
        when(validator.validateAndHash(anyString(), anyString())).thenReturn("a".repeat(64));
        properties = new SysProperties();
        ProfileProperties profile = new ProfileProperties();
        profile.setFrequencyOfRepeatedSubmissions(1);
        properties.setProfile(profile);
        user = mock(LoginUser.class);
        when(user.getUserId()).thenReturn(7L);
    }

    /** 两个请求均通过查询和频率检查后竞争插入，只能生成一份提交及任务。 */
    @Test
    void concurrentRequestsReplayOneCommittedSubmission() throws Exception {
        CyclicBarrier beforeInsert = new CyclicBarrier(2);
        SubmissionMapper competing = mock(SubmissionMapper.class, delegatesTo(submissions));
        doAnswer(call -> {
            beforeInsert.await(10, TimeUnit.SECONDS);
            return submissions.insert((Submission) call.getArgument(0));
        }).when(competing).insert(any(Submission.class));
        SubmissionService regular = regular(competing, tasks);
        Submission2JudgeDto dto = dto();
        SubmissionRequestIdentity identity = SubmissionRequestIdentity.regular(dto);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<SubmissionVo> request = () -> idempotency.execute(7L, identity,
                    () -> regular.submitProblemToJudge(dto, user, identity));
            Future<SubmissionVo> first = executor.submit(request);
            Future<SubmissionVo> second = executor.submit(request);
            assertEquals(first.get(20, TimeUnit.SECONDS).getSubmissionId(), second.get(20, TimeUnit.SECONDS).getSubmissionId());
            assertCounts(1);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 任务插入失败时提交也回滚，相同 UUID 之后可以成功重试。 */
    @Test
    void taskFailureRollsBackAndSameRequestCanRetry() {
        JudgeTaskMapper failing = mock(JudgeTaskMapper.class);
        when(failing.insert(any(JudgeTask.class))).thenThrow(new DataAccessResourceFailureException("模拟任务保存失败"));
        SubmissionService regular = regular(submissions, failing);
        Submission2JudgeDto dto = dto();
        SubmissionRequestIdentity identity = SubmissionRequestIdentity.regular(dto);
        assertThrows(DataAccessResourceFailureException.class, () -> idempotency.execute(7L, identity,
                () -> regular.submitProblemToJudge(dto, user, identity)));
        assertCounts(0);
        SubmissionService healthy = regular(submissions, tasks);
        SubmissionVo accepted = idempotency.execute(7L, identity,
                () -> healthy.submitProblemToJudge(dto, user, identity));
        assertEquals(accepted.getSubmissionId(), idempotency.execute(7L, identity,
                () -> { throw new AssertionError("已成功提交不应再次创建"); }).getSubmissionId());
        assertCounts(1);
    }

    /** 比赛正式提交和赛后普通提交均回放原结果，不重新解释比赛阶段。 */
    @Test
    void contestReplayAndRegularFallbackPreserveOriginalIdentity() {
        for (boolean fallback : new boolean[]{false, true}) {
            jdbc.update("DELETE FROM judge_task");
            jdbc.update("DELETE FROM submission");
            ContestMapper contests = mock(ContestMapper.class);
            Contest current = new Contest();
            current.setStatus(fallback ? ContestStatus.FINISH : ContestStatus.RUNNING);
            current.setStartTime(LocalDateTime.now().minusHours(1));
            current.setEndTime(LocalDateTime.now().plusHours(1));
            when(contests.selectOne(any())).thenReturn(current);
            ContestParticipateMapper participants = mock(ContestParticipateMapper.class);
            when(participants.selectCount(any())).thenReturn(1L);
            ContestProblemMapper problems = mock(ContestProblemMapper.class);
            ContestProblem problem = new ContestProblem();
            problem.setProblemId(1L);
            problem.setReleaseStatus(fallback ? ContestProblemStatus.PUBLISH : ContestProblemStatus.OPENING);
            if (fallback) when(problems.selectOne(any())).thenReturn(null, problem);
            else when(problems.selectOne(any())).thenReturn(problem);
            ContestSubmissionServiceImpl target = new ContestSubmissionServiceImpl(submissions, problems, tasks,
                    participants, regular(submissions, tasks), properties, validator, mock(PageFactory.class), contests, mock(com.gusl.gojserver.submission.application.SubmissionStatusService.class), mock(UserMapper.class));
            ReflectionTestUtils.setField(target, "maxAttempts", 3);
            ContestSubmissionService contest = proxy(target, ContestSubmissionService.class);
            ContestSubmission2JudgeDto dto = new ContestSubmission2JudgeDto();
            dto.setRequestId(UUID.randomUUID().toString());
            dto.setLanguage("py3");
            dto.setSourceCode("print(1)");
            SubmissionRequestIdentity identity = SubmissionRequestIdentity.contest(10L, "A", dto);
            SubmissionVo result = idempotency.execute(7L, identity,
                    () -> contest.submitContestProblemToJudge(10L, "A", dto, user, identity));
            assertEquals(fallback ? "REGULAR" : "CONTEST", result.getSubmissionType());
            current.setStatus(ContestStatus.WAITING);
            assertEquals(result.getSubmissionId(), idempotency.execute(7L, identity,
                    () -> contest.submitContestProblemToJudge(10L, "A", dto, user, identity)).getSubmissionId());
            assertEquals(identity.getRequestSha256(), submissions.selectById(result.getSubmissionId()).getRequestSha256());
            dto.setSourceCode("print(2)");
            assertEquals(409, assertThrows(SubmissionRequestException.class, () -> idempotency.execute(7L,
                    SubmissionRequestIdentity.contest(10L, "A", dto), () -> null)).getStatus());
            assertCounts(1);
        }
    }

    /** 同 UUID 在不同用户之间互不影响，不同 UUID 仍受原频率限制。 */
    @Test
    void scopesByUserAndPreservesFrequencyLimit() {
        SubmissionService regular = regular(submissions, tasks);
        Submission2JudgeDto dto = dto();
        SubmissionRequestIdentity identity = SubmissionRequestIdentity.regular(dto);
        idempotency.execute(7L, identity, () -> regular.submitProblemToJudge(dto, user, identity));
        LoginUser other = mock(LoginUser.class);
        when(other.getUserId()).thenReturn(8L);
        idempotency.execute(8L, identity, () -> regular.submitProblemToJudge(dto, other, identity));
        dto.setRequestId(UUID.randomUUID().toString());
        SubmissionRequestIdentity next = SubmissionRequestIdentity.regular(dto);
        assertEquals(422, assertThrows(SubmissionRequestException.class, () -> idempotency.execute(7L, next,
                () -> regular.submitProblemToJudge(dto, user, next))).getStatus());
        assertCounts(2);
    }

    /** 构建真实普通提交 Service，仅替换无关的题目校验依赖。 */
    private SubmissionService regular(SubmissionMapper mapper, JudgeTaskMapper taskMapper) {
        ProblemMapper problems = mock(ProblemMapper.class);
        ProblemTestDataMapper datasets = mock(ProblemTestDataMapper.class);
        when(problems.selectCount(any())).thenReturn(1L);
        when(datasets.selectCount(any())).thenReturn(1L);
        SubmissionServiceImpl target = new SubmissionServiceImpl(mapper, mock(UserMapper.class), problems,
                datasets, taskMapper, validator, properties, mock(PageFactory.class), mock(com.gusl.gojserver.submission.application.SubmissionStatusService.class));
        ReflectionTestUtils.setField(target, "maxAttempts", 3);
        return proxy(target, SubmissionService.class);
    }

    /** 使用注解事务拦截器，确保测试真实经过提交或回滚边界。 */
    private <T> T proxy(Object target, Class<T> type) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return type.cast(factory.getProxy());
    }

    /** 构造一次新的普通提交。 */
    private Submission2JudgeDto dto() {
        return new Submission2JudgeDto(UUID.randomUUID().toString(), 1L, "py3", "print(1)", null);
    }

    /** 同时核对提交与任务数量，避免只有提交成功而任务丢失。 */
    private void assertCounts(int expected) {
        assertEquals(expected, jdbc.queryForObject("SELECT COUNT(*) FROM submission", Integer.class));
        assertEquals(expected, jdbc.queryForObject("SELECT COUNT(*) FROM judge_task", Integer.class));
    }
}
