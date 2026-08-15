package com.gusl.gojserver.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gusl.common.constant.JudgeQueueConstant;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.common.pojo.entity.ProblemReviewSubmission;
import com.gusl.common.pojo.entity.ProblemTestData;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojserver.mapper.ProblemMapper;
import com.gusl.gojserver.mapper.ProblemReviewSubmissionMapper;
import com.gusl.gojserver.mapper.ProblemTestDataMapper;
import com.gusl.gojserver.mapper.SubmissionMapper;
import com.gusl.gojserver.mapper.UserMapper;
import com.gusl.gojserver.pojo.dto.ProblemReviewJudgeDto;
import com.gusl.gojserver.pojo.dto.SubmissionDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.service.support.JudgeSourceValidator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 普通提交与管理员验题提交的落库和队列隔离测试。
 */
class JudgeSubmissionIsolationTests {

    /**
     * 纯单元测试没有启动 MyBatis 上下文，需要预先初始化 Lambda 列名缓存。
     */
    @BeforeAll
    static void initializeTableInfo() {
        initializeTableInfo(Problem.class);
        initializeTableInfo(ProblemTestData.class);
        initializeTableInfo(Submission.class);
        initializeTableInfo(ProblemReviewSubmission.class);
    }

    /**
     * 普通用户提交只写 submission 表并进入普通提交队列。
     */
    @Test
    void normalSubmissionUsesOnlyNormalSubmissionTableAndQueue() {
        SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
        ProblemMapper problemMapper = mock(ProblemMapper.class);
        ProblemTestDataMapper testDataMapper = mock(ProblemTestDataMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ListOperations<String, String> listOperations = mock(ListOperations.class);
        JudgeSourceValidator sourceValidator = mock(JudgeSourceValidator.class);
        LoginUser loginUser = mock(LoginUser.class);

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(problemMapper.selectCount(any())).thenReturn(1L);
        when(testDataMapper.selectCount(any())).thenReturn(1L);
        when(submissionMapper.selectCount(any())).thenReturn(0L);
        when(sourceValidator.validateAndHash("java11", "class Main {}"))
                .thenReturn("normal-source-hash");
        when(loginUser.getUserId()).thenReturn(5L);
        doAnswer(invocation -> {
            Submission submission = invocation.getArgument(0);
            submission.setId(101L);
            return 1;
        }).when(submissionMapper).insert(any(Submission.class));

        SubmissionServiceImpl service = new SubmissionServiceImpl(
                submissionMapper,
                redisTemplate,
                mock(UserMapper.class),
                problemMapper,
                testDataMapper,
                sourceValidator
        );
        SubmissionDto dto = new SubmissionDto();
        dto.setProblemId(7L);
        dto.setLanguage("java11");
        dto.setSourceCode("class Main {}");

        Long submissionId = service.submitProblemToJudge(dto, loginUser);

        assertEquals(101L, submissionId);
        verify(submissionMapper).insert(any(Submission.class));
        verify(listOperations).leftPush(JudgeQueueConstant.SUBMISSION_READY_QUEUE, "101");
        verify(listOperations, never()).leftPush(
                eq(JudgeQueueConstant.PROBLEM_REVIEW_READY_QUEUE),
                anyString()
        );
    }

    /**
     * 管理员验题只写 problem_review_submission 表并进入验题队列。
     */
    @Test
    void reviewSubmissionUsesOnlyReviewSubmissionTableAndQueue() {
        ProblemReviewSubmissionMapper reviewSubmissionMapper = mock(ProblemReviewSubmissionMapper.class);
        ProblemMapper problemMapper = mock(ProblemMapper.class);
        ProblemTestDataMapper testDataMapper = mock(ProblemTestDataMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ListOperations<String, String> listOperations = mock(ListOperations.class);
        JudgeSourceValidator sourceValidator = mock(JudgeSourceValidator.class);
        LoginUser loginUser = mock(LoginUser.class);

        ProblemTestData testData = ProblemTestData.builder().id(12L).problemId(7L).build();
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(problemMapper.selectCount(any())).thenReturn(1L);
        when(testDataMapper.selectOne(any())).thenReturn(testData);
        when(reviewSubmissionMapper.selectCount(any())).thenReturn(0L);
        when(sourceValidator.validateAndHash("java11", "class Main {}"))
                .thenReturn("review-source-hash");
        when(loginUser.getUserId()).thenReturn(9L);
        doAnswer(invocation -> {
            ProblemReviewSubmission submission = invocation.getArgument(0);
            submission.setId(202L);
            return 1;
        }).when(reviewSubmissionMapper).insert(any(ProblemReviewSubmission.class));

        ProblemReviewJudgeServiceImpl service = new ProblemReviewJudgeServiceImpl(
                reviewSubmissionMapper,
                problemMapper,
                testDataMapper,
                redisTemplate,
                sourceValidator
        );
        ProblemReviewJudgeDto dto = new ProblemReviewJudgeDto();
        dto.setLanguage("java11");
        dto.setSourceCode("class Main {}");

        Long reviewSubmissionId = service.submit(7L, dto, loginUser);

        assertEquals(202L, reviewSubmissionId);
        verify(reviewSubmissionMapper).insert(any(ProblemReviewSubmission.class));
        verify(listOperations).leftPush(JudgeQueueConstant.PROBLEM_REVIEW_READY_QUEUE, "202");
        verify(listOperations, never()).leftPush(
                eq(JudgeQueueConstant.SUBMISSION_READY_QUEUE),
                anyString()
        );
    }

    private static void initializeTableInfo(Class<?> entityType) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
