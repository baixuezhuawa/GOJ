package com.gusl.gojserver.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gusl.common.common.BaseException;
import com.gusl.common.constant.ProblemStatus;
import com.gusl.common.constant.ProblemTestDataStatus;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.common.pojo.entity.ProblemTestData;
import com.gusl.gojserver.config.properties.JudgeProperties;
import com.gusl.gojserver.mapper.ProblemMapper;
import com.gusl.gojserver.mapper.ProblemTestDataMapper;
import com.gusl.gojserver.mapper.TagMapper;
import com.gusl.gojserver.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 题目审核通过流程测试。
 */
class ProblemReviewServiceImplTests {

    private static final Long PROBLEM_ID = 7L;

    Path dataRoot;

    private ProblemMapper problemMapper;
    private ProblemTestDataMapper testDataMapper;
    private TransactionTemplate transactionTemplate;
    private ProblemReviewServiceImpl service;
    private Path stagingDataDirectory;

    @BeforeEach
    void setUp() throws IOException {
        initializeTableInfo(Problem.class);
        initializeTableInfo(ProblemTestData.class);
        Files.createDirectories(Path.of("target"));
        dataRoot = Files.createTempDirectory(Path.of("target"), "problem-review-");
        problemMapper = mock(ProblemMapper.class);
        testDataMapper = mock(ProblemTestDataMapper.class);
        transactionTemplate = mock(TransactionTemplate.class);

        JudgeProperties judgeProperties = new JudgeProperties();
        judgeProperties.setDataRoot(dataRoot.toString());
        service = new ProblemReviewServiceImpl(
                problemMapper,
                testDataMapper,
                mock(UserMapper.class),
                mock(TagMapper.class),
                judgeProperties,
                transactionTemplate
        );

        stagingDataDirectory = dataRoot.resolve("staging/upload-11/extracted");
        Files.createDirectories(stagingDataDirectory.resolve("test1"));
        Files.writeString(stagingDataDirectory.resolve("test1/input.txt"), "1 2\n");
        Files.writeString(stagingDataDirectory.resolve("test1/output.txt"), "3\n");

        Problem problem = new Problem();
        problem.setId(PROBLEM_ID);
        problem.setStatus(ProblemStatus.PENDING);

        ProblemTestData testData = ProblemTestData.builder()
                .id(11L)
                .problemId(PROBLEM_ID)
                .testNodeCount(1)
                .storagePath(stagingDataDirectory.toString())
                .status(ProblemTestDataStatus.EXTRACTED)
                .active(false)
                .build();

        when(problemMapper.selectOne(any())).thenReturn(problem);
        when(testDataMapper.selectOne(any())).thenReturn(testData);
        when(testDataMapper.selectList(any())).thenReturn(List.of());
        when(problemMapper.update(any())).thenReturn(1);

        // 测试中直接执行事务回调，以覆盖目录移动和补偿逻辑。
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    /**
     * 纯单元测试没有启动 MyBatis 上下文，需要显式初始化 Lambda 列名缓存。
     */
    private void initializeTableInfo(Class<?> entityType) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, entityType);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (dataRoot == null || !Files.exists(dataRoot)) {
            return;
        }
        try (var paths = Files.walk(dataRoot)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void approveMovesStagingDataToFirstOfficialVersion() {
        when(testDataMapper.update(any())).thenReturn(1);

        service.approve(PROBLEM_ID);

        Path officialData = dataRoot.resolve("testData/p7/v1/test1/input.txt");
        assertTrue(Files.isRegularFile(officialData));
        assertFalse(Files.exists(stagingDataDirectory));
    }

    @Test
    void approveRestoresStagingDataWhenDatabaseUpdateFails() {
        // 第一次更新退休旧版本，第二次更新当前数据集时模拟并发状态变化。
        when(testDataMapper.update(any())).thenReturn(1, 0);

        assertThrows(BaseException.class, () -> service.approve(PROBLEM_ID));

        assertTrue(Files.isRegularFile(stagingDataDirectory.resolve("test1/input.txt")));
        assertFalse(Files.exists(dataRoot.resolve("testData/p7/v1")));
    }
}
