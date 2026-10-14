package com.gusl.gojjudge.judging;

import com.gusl.common.constant.JudgingConstant;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import com.gusl.common.constant.ProblemStatus;
import com.gusl.common.constant.ProblemType;
import com.gusl.gojjudge.judging.application.engine.JudgeRequestConfigurer;
import com.gusl.gojjudge.judging.infrastructure.persistence.ProblemCheckerMapper;
import com.gusl.common.pojo.entity.JudgeTaskMessage;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.common.pojo.entity.ProblemReviewSubmission;
import com.gusl.common.pojo.entity.ProblemTestData;
import com.gusl.gojjudge.judging.application.handler.ProblemReviewJudgeHandler;
import com.gusl.gojjudge.judging.application.handler.SubmissionJudgeContext;
import com.gusl.gojjudge.judging.application.result.SubmissionResultService;
import com.gusl.gojjudge.judging.infrastructure.persistence.ProblemMapper;
import com.gusl.gojjudge.judging.infrastructure.persistence.ProblemReviewSubmissionMapper;
import com.gusl.gojjudge.judging.infrastructure.persistence.ProblemTestDataMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证 Worker 可读取草稿题目的未启用版本和被覆盖后的数据路径。 */
class ProblemReviewJudgeHandlerTests {

    @Test
    void loadsCurrentContentForReferencedDatasetWithoutReviewState() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ProblemTestData.class);
        ProblemReviewSubmissionMapper submissions = mock(ProblemReviewSubmissionMapper.class);
        ProblemMapper problems = mock(ProblemMapper.class);
        ProblemTestDataMapper datasets = mock(ProblemTestDataMapper.class);
        ProblemReviewSubmission submission = ProblemReviewSubmission.builder().id(1L).problemId(2L)
                .problemTestDataId(3L).status(JudgingConstant.IN_QUEUE).sourceCode("code").language("java21").build();
        Problem problem = new Problem();
        problem.setId(2L);
        problem.setStatus(ProblemStatus.DRAFT);
        problem.setProblemType(ProblemType.NORMAL);
        problem.setTimeLimit(1000);
        problem.setMemoryLimit(65536);
        ProblemTestData data = ProblemTestData.builder().id(3L).problemId(2L).version(8).active(false)
                .storagePath("testData/p2/v8-new/cases").testNodeCount(2).build();
        when(submissions.selectById(1L)).thenReturn(submission);
        when(problems.selectById(2L)).thenReturn(problem);
        when(datasets.selectOne(any())).thenReturn(data);
        ProblemReviewJudgeHandler handler = new ProblemReviewJudgeHandler(submissions, problems, datasets,
                mock(SubmissionResultService.class), new JudgeRequestConfigurer(mock(ProblemCheckerMapper.class)));
        JudgeTaskMessage message = new JudgeTaskMessage();
        message.setBusinessId(1L);

        SubmissionJudgeContext context = handler.prepare(message);
        assertEquals("testData/p2/v8-new/cases", context.getExecutionRequest().getTestDataRef().getStoragePath());
        assertEquals(3L, context.getExecutionRequest().getTestDataRef().getTestDataId());
        assertEquals(8, context.getExecutionRequest().getTestDataRef().getVersion());
        ArgumentCaptor<LambdaQueryWrapper<ProblemTestData>> capture = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(datasets).selectOne(capture.capture());
        assertFalse(capture.getValue().getSqlSegment().contains("active"));
        assertFalse(capture.getValue().getSqlSegment().contains("status"));
    }
}
