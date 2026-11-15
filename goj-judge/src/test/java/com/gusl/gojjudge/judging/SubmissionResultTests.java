package com.gusl.gojjudge.judging;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.constant.SystemConstant;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojjudge.judging.result.SubmissionFinalizedUpdater;
import com.gusl.gojjudge.judging.result.SubmissionResultServiceImpl;
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
    private final SubmissionResultServiceImpl service = new SubmissionResultServiceImpl(submissions,
            mock(ContestSubmissionMapper.class), mock(ProblemReviewSubmissionMapper.class), List.of(updater));
    private final Submission submission = new Submission();
    private final JudgeOutcome outcome = new JudgeOutcome();

    @BeforeEach
    void prepare() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Submission.class);
        submission.setId(1L);
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

    @Test
    void statisticFailurePropagatesToCaller() {
        doThrow(new IllegalStateException("统计写入失败")).when(updater).update(any());
        assertThrows(IllegalStateException.class, () -> service.updateSubmission(submission, outcome));
    }
}
