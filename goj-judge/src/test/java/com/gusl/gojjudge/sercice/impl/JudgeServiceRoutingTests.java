package com.gusl.gojjudge.sercice.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gusl.common.pojo.entity.ProblemReviewSubmission;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojjudge.client.GoJudgeClient;
import com.gusl.gojjudge.mapper.ProblemMapper;
import com.gusl.gojjudge.mapper.ProblemReviewSubmissionMapper;
import com.gusl.gojjudge.mapper.ProblemTestDataMapper;
import com.gusl.gojjudge.mapper.SubmissionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Judge 两类任务入口的 Mapper 路由隔离测试。
 */
class JudgeServiceRoutingTests {

    /**
     * 纯单元测试没有启动 MyBatis 上下文，需要预先初始化 Lambda 列名缓存。
     */
    @BeforeAll
    static void initializeTableInfo() {
        initializeTableInfo(Submission.class);
        initializeTableInfo(ProblemReviewSubmission.class);
    }

    /**
     * 普通提交只从 submission 表领取任务并向该表写回结果。
     */
    @Test
    void normalSubmissionUsesOnlySubmissionMapper() {
        SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
        ProblemReviewSubmissionMapper reviewSubmissionMapper = mock(ProblemReviewSubmissionMapper.class);
        Submission submission = new Submission();
        submission.setId(101L);
        submission.setProblemId(7L);
        submission.setLanguage("unsupported-language");
        submission.setSourceCode("source");

        when(submissionMapper.update(any())).thenReturn(1);
        when(submissionMapper.selectOne(any())).thenReturn(submission);
        JudgeServiceImpl service = createService(submissionMapper, reviewSubmissionMapper);

        service.judgeSubmission(101L);

        // 第一次更新领取任务，第二次更新写回公共流程产生的系统错误终态。
        verify(submissionMapper, times(2)).update(any());
        verify(submissionMapper).selectOne(any());
        verifyNoInteractions(reviewSubmissionMapper);
    }

    /**
     * 管理员验题只从独立验题表领取任务并向该表写回结果。
     */
    @Test
    void reviewSubmissionUsesOnlyReviewSubmissionMapper() {
        SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
        ProblemReviewSubmissionMapper reviewSubmissionMapper = mock(ProblemReviewSubmissionMapper.class);
        ProblemReviewSubmission submission = new ProblemReviewSubmission();
        submission.setId(202L);
        submission.setProblemId(7L);
        submission.setProblemTestDataId(12L);
        submission.setLanguage("unsupported-language");
        submission.setSourceCode("source");

        when(reviewSubmissionMapper.update(any())).thenReturn(1);
        when(reviewSubmissionMapper.selectOne(any())).thenReturn(submission);
        JudgeServiceImpl service = createService(submissionMapper, reviewSubmissionMapper);

        service.judgeProblemReview(202L);

        // 第一次更新领取任务，第二次更新写回公共流程产生的系统错误终态。
        verify(reviewSubmissionMapper, times(2)).update(any());
        verify(reviewSubmissionMapper).selectOne(any());
        verifyNoInteractions(submissionMapper);
    }

    private JudgeServiceImpl createService(
            SubmissionMapper submissionMapper,
            ProblemReviewSubmissionMapper reviewSubmissionMapper
    ) {
        return new JudgeServiceImpl(
                submissionMapper,
                reviewSubmissionMapper,
                mock(ProblemMapper.class),
                mock(ProblemTestDataMapper.class),
                mock(GoJudgeClient.class),
                List.of()
        );
    }

    private static void initializeTableInfo(Class<?> entityType) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
