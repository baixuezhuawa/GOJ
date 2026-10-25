package com.gusl.gojjudge.judging;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.constant.SubmissionType;
import com.gusl.common.constant.SystemConstant;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojjudge.judging.result.SubmissionFinalizedUpdater;
import com.gusl.gojjudge.judging.result.SubmissionResultService;
import com.gusl.gojjudge.judging.model.JudgeOutcome;
import com.gusl.gojjudge.judging.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证回写返回值和终态统计触发条件，不将 Mock 测试当作真实数据库事务验证。 */
class SubmissionResultTests {
    private final SubmissionMapper submissions = mock(SubmissionMapper.class);
    private final SubmissionFinalizedUpdater updater = mock(SubmissionFinalizedUpdater.class);
    private final org.springframework.context.ApplicationEventPublisher events = mock(org.springframework.context.ApplicationEventPublisher.class);
    private final SubmissionResultService service = new SubmissionResultService(submissions, List.of(updater), events);
    private final Submission submission = new Submission();
    private final JudgeOutcome outcome = new JudgeOutcome();

    @BeforeEach
    void prepare() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Submission.class);
        submission.setId(1L);
        submission.setSubmissionType(SubmissionType.REGULAR);
        submission.setStatus(JudgingConstant.RUNNING);
        when(submissions.update(any())).thenReturn(1);
        outcome.setCurStatus(JudgingConstant.ACCEPTED);
    }

    @Test
    void firstTerminalWriteUpdatesStatistics() {
        assertTrue(service.updateSubmission(submission, outcome));
        verify(updater).update(any());
    }

    @Test
    void progressAndSystemErrorDoNotUpdateStatistics() {
        outcome.setCurStatus(JudgingConstant.RUNNING);
        assertTrue(service.updateSubmission(submission, outcome));
        outcome.setCurStatus(SystemConstant.SYSTEM_ERROR);
        assertTrue(service.updateSubmission(submission, outcome));
        verifyNoInteractions(updater);
    }

    @Test
    void duplicateTerminalIsIdempotentButMissingRecordIsRejected() {
        when(submissions.update(any())).thenReturn(0);
        submission.setStatus(JudgingConstant.WRONG_ANSWER);
        when(submissions.selectById(1L)).thenReturn(submission);
        assertTrue(service.updateSubmission(submission, outcome));
        when(submissions.selectById(1L)).thenReturn(null);
        assertFalse(service.updateSubmission(submission, outcome));
        verifyNoInteractions(updater);
    }

    /** 比赛和验题也写回统一表，但不能污染个人做题统计。 */
    @Test
    void contestAndReviewDoNotUpdatePersonalStatistics() {
        for (String type : List.of(SubmissionType.CONTEST, SubmissionType.PROBLEM_REVIEW)) {
            submission.setSubmissionType(type);
            assertTrue(service.updateSubmission(submission, outcome));
        }
        verify(submissions, times(2)).update(any());
        verifyNoInteractions(updater);
    }

    @Test
    void statisticFailurePropagatesToCaller() {
        doThrow(new IllegalStateException("统计写入失败")).when(updater).update(any());
        assertThrows(IllegalStateException.class, () -> service.updateSubmission(submission, outcome));
    }

    /** 消息包含当前提交全部计分字段，仅成功写回的终态发布一次。 */
    @Test
    void contestPublishesOnlySuccessfulTerminalWithSevenFields() {
        submission.setSubmissionType(SubmissionType.CONTEST);
        submission.setContestId(7L);
        submission.setProblemId(8L);
        submission.setUserId(9L);
        submission.setVersion(2);
        submission.setSubmissionTime(java.time.LocalDateTime.of(2026, 10, 21, 10, 5));
        outcome.setCurStatus(JudgingConstant.RUNNING);
        service.updateSubmission(submission, outcome);
        verifyNoInteractions(events);
        outcome.setCurStatus(JudgingConstant.ACCEPTED);
        service.updateSubmission(submission, outcome);
        when(submissions.update(any())).thenReturn(0);
        submission.setStatus(JudgingConstant.ACCEPTED);
        when(submissions.selectById(1L)).thenReturn(submission);
        service.updateSubmission(submission, outcome);
        org.mockito.ArgumentCaptor<com.gusl.common.pojo.entity.ContestJudgeResultMessage> capture =
                org.mockito.ArgumentCaptor.forClass(com.gusl.common.pojo.entity.ContestJudgeResultMessage.class);
        verify(events).publishEvent(capture.capture());
        assertEquals(7L, capture.getValue().getContestId());
        assertEquals(8L, capture.getValue().getProblemId());
        assertEquals(9L, capture.getValue().getUserId());
        assertEquals(1L, capture.getValue().getSubmissionId());
        assertEquals(2, capture.getValue().getVersion());
        assertEquals(JudgingConstant.ACCEPTED, capture.getValue().getStatus());
        assertEquals(submission.getSubmissionTime(), capture.getValue().getSubmissionTime());
        verifyNoInteractions(updater);
    }
}
