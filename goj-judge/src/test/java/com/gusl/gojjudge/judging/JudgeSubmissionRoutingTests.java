package com.gusl.gojjudge.judging;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gusl.common.constant.*;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojjudge.judging.JudgeServiceImpl;
import com.gusl.gojjudge.judging.engine.*;
import com.gusl.gojjudge.judging.result.SubmissionResultService;
import com.gusl.gojjudge.judging.exception.JudgeSystemException;
import com.gusl.gojjudge.judging.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证去掉 Handler 后，提交的数据来源、业务回写和异常传播仍正确。 */
class JudgeSubmissionRoutingTests {

    private final SubmissionMapper submissions = mock(SubmissionMapper.class);
    private final ContestSubmissionMapper contests = mock(ContestSubmissionMapper.class);
    private final ProblemReviewSubmissionMapper reviews = mock(ProblemReviewSubmissionMapper.class);
    private final ProblemMapper problems = mock(ProblemMapper.class);
    private final ProblemTestDataMapper datasets = mock(ProblemTestDataMapper.class);
    private final ContestProblemMapper contestProblems = mock(ContestProblemMapper.class);
    private final SubmissionResultService results = mock(SubmissionResultService.class);
    private final JudgeEngine engine = mock(JudgeEngine.class);
    private final JudgeServiceImpl service = new JudgeServiceImpl(submissions, reviews, contests, results,
            problems, datasets, contestProblems, new JudgeRequestConfigurer(mock(ProblemCheckerMapper.class)), engine);
    private final Submission submission = new Submission();
    private final ContestSubmission contest = new ContestSubmission();

    /** 建立普通题和两个不同来源的数据引用，真实 Service 负责组装。 */
    @BeforeEach
    void prepare() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ProblemTestData.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ContestProblem.class);
        Problem problem = new Problem();
        problem.setId(2L);
        problem.setProblemType(ProblemType.NORMAL);
        problem.setTimeLimit(1000);
        problem.setMemoryLimit(65536);
        when(problems.selectById(2L)).thenReturn(problem);
        submission.setId(1L);
        submission.setProblemId(2L);
        submission.setStatus(JudgingConstant.IN_QUEUE);
        submission.setSourceCode("普通代码");
        submission.setLanguage("py3");
        when(submissions.selectById(1L)).thenReturn(submission);
        contest.setId(3L);
        contest.setContestId(4L);
        contest.setProblemId(2L);
        contest.setStatus(JudgingConstant.IN_QUEUE);
        contest.setSourceCode("比赛代码");
        contest.setLanguage("py3");
        when(contests.selectById(3L)).thenReturn(contest);
        when(results.updateSubmission(eq(submission), any())).thenReturn(true);
        when(results.updateSubmission(eq(contest), any())).thenReturn(true);
        doAnswer(invocation -> {
            JudgeSession session = invocation.getArgument(0);
            session.status(JudgingConstant.WRONG_ANSWER).push();
            return null;
        }).when(engine).judge(any());
    }

    @Test
    void normalSubmissionUsesActiveDatasetAndNormalWriter() {
        when(datasets.selectOne(any())).thenReturn(dataset(7L));
        service.judge(message(JudgeTaskType.SUBMISSION, 1L));
        ArgumentCaptor<JudgeSession> session = ArgumentCaptor.forClass(JudgeSession.class);
        verify(engine).judge(session.capture());
        assertEquals(7L, session.getValue().getRequest().getTestDataRef().getTestDataId());
        assertTrue(session.getValue().isFinished());
        ArgumentCaptor<LambdaQueryWrapper<ProblemTestData>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(datasets).selectOne(query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("active"));
        verify(results, times(2)).updateSubmission(eq(submission), any());
        verify(results, never()).updateSubmission(any(ContestSubmission.class), any());
    }

    @Test
    void contestSubmissionUsesBoundDatasetAndContestWriter() {
        ContestProblem binding = new ContestProblem();
        binding.setTestDataId(8L);
        when(contestProblems.selectOne(any())).thenReturn(binding);
        when(datasets.selectById(8L)).thenReturn(dataset(8L));
        service.judge(message(JudgeTaskType.CONTEST_SUBMISSION, 3L));
        ArgumentCaptor<JudgeSession> session = ArgumentCaptor.forClass(JudgeSession.class);
        verify(engine).judge(session.capture());
        assertEquals(8L, session.getValue().getRequest().getTestDataRef().getTestDataId());
        assertEquals("比赛代码", session.getValue().getRequest().getSourceCode());
        verify(datasets, never()).selectOne(any());
        verify(results, times(2)).updateSubmission(eq(contest), any());
        verify(results, never()).updateSubmission(any(Submission.class), any());
    }

    @Test
    void terminalSubmissionSkipsPreparationAndExecution() {
        submission.setStatus(JudgingConstant.ACCEPTED);
        service.judge(message(JudgeTaskType.SUBMISSION, 1L));
        verifyNoInteractions(problems, datasets, engine, results);
    }

    @Test
    void engineReturningWithoutFinalWriteIsFailure() {
        when(datasets.selectOne(any())).thenReturn(dataset(7L));
        doNothing().when(engine).judge(any());
        assertThrows(JudgeSystemException.class, () -> service.judge(message(JudgeTaskType.SUBMISSION, 1L)));
        ArgumentCaptor<JudgeSession> session = ArgumentCaptor.forClass(JudgeSession.class);
        verify(engine).judge(session.capture());
        assertTrue(session.getValue().isClosed());
        assertFalse(session.getValue().isFinished());
    }

    @Test
    void initialWriteRejectionNeverStartsEngine() {
        when(datasets.selectOne(any())).thenReturn(dataset(7L));
        when(results.updateSubmission(eq(submission), any())).thenReturn(false);
        assertThrows(JudgeSystemException.class, () -> service.judge(message(JudgeTaskType.SUBMISSION, 1L)));
        verifyNoInteractions(engine);
    }

    /** 创建任务消息，只填本组测试涉及的提交类型与业务主键。 */
    private JudgeTaskMessage message(String type, Long id) {
        JudgeTaskMessage message = new JudgeTaskMessage();
        message.setTaskType(type);
        message.setBusinessId(id);
        return message;
    }

    /** 创建可供 Service 装配的测试数据记录。 */
    private ProblemTestData dataset(Long id) {
        return ProblemTestData.builder().id(id).problemId(2L).storagePath("cases").testNodeCount(2).version(1).build();
    }
}
