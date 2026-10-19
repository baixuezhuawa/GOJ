package com.gusl.gojserver.problem;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gusl.common.common.BaseException;
import com.gusl.common.constant.SubmissionType;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojserver.account.infrastructure.security.LoginUser;
import com.gusl.gojserver.account.infrastructure.persistence.UserMapper;
import com.gusl.gojserver.judgetask.application.support.JudgeSourceValidator;
import com.gusl.gojserver.judgetask.infrastructure.persistence.JudgeTaskMapper;
import com.gusl.gojserver.problem.application.ProblemReviewJudgeServiceImpl;
import com.gusl.gojserver.problem.application.dto.ProblemReviewJudgeDto;
import com.gusl.gojserver.problem.infrastructure.persistence.ProblemMapper;
import com.gusl.gojserver.problem.infrastructure.persistence.ProblemTestDataMapper;
import com.gusl.gojserver.submission.application.SubmissionServiceImpl;
import com.gusl.gojserver.submission.infrastructure.persistence.SubmissionMapper;
import com.gusl.gojserver.shared.application.pagination.PageFactory;
import com.gusl.gojserver.shared.infrastructure.config.properties.SysProperties;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 合表后验证验题落库、真实提交人及普通详情的场景隔离。 */
class UnifiedSubmissionTests {
    private final SubmissionMapper submissions = mock(SubmissionMapper.class);
    private final ProblemMapper problems = mock(ProblemMapper.class);
    private final ProblemTestDataMapper datasets = mock(ProblemTestDataMapper.class);
    private final JudgeTaskMapper tasks = mock(JudgeTaskMapper.class);
    private final JudgeSourceValidator validator = mock(JudgeSourceValidator.class);
    private final ProblemReviewJudgeServiceImpl reviews = new ProblemReviewJudgeServiceImpl(
            submissions, problems, datasets, tasks, validator);

    /** 初始化 MyBatis-Plus 的字段映射，使捕获的查询条件可以生成 SQL。 */
    @BeforeEach
    void prepare() {
        for (Class<?> type : new Class<?>[]{Submission.class, Problem.class, ProblemTestData.class}) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
    }

    /** 验题不传版本，也能提交尚未启用的数据题目。 */
    @Test
    void reviewCreatesUnifiedSubmissionAndTaskWithoutDataReference() {
        when(problems.selectCount(any())).thenReturn(1L);
        when(datasets.selectOne(any())).thenReturn(ProblemTestData.builder().version(8).active(false).build());
        when(validator.validateAndHash("py3", "print(1)")).thenReturn("hash");
        when(submissions.insert(any(Submission.class))).thenAnswer(call -> {
            Submission submission = call.getArgument(0);
            submission.setId(42L);
            return 1;
        });
        ProblemReviewJudgeDto dto = new ProblemReviewJudgeDto();
        dto.setLanguage("py3");
        dto.setSourceCode("print(1)");
        LoginUser user = mock(LoginUser.class);
        when(user.getUserId()).thenReturn(9L);

        assertEquals(42L, reviews.submit(2L, dto, user));
        ArgumentCaptor<Submission> submitted = ArgumentCaptor.forClass(Submission.class);
        verify(submissions).insert(submitted.capture());
        assertEquals(SubmissionType.PROBLEM_REVIEW, submitted.getValue().getSubmissionType());
        assertEquals(9L, submitted.getValue().getUserId());
        assertNull(submitted.getValue().getContestId());
        ArgumentCaptor<JudgeTask> task = ArgumentCaptor.forClass(JudgeTask.class);
        verify(tasks).insert(task.capture());
        assertEquals(42L, task.getValue().getBusinessId());
        ArgumentCaptor<LambdaQueryWrapper<ProblemTestData>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(datasets).selectOne(query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("ORDER BY version DESC LIMIT 1"));
        assertFalse(query.getValue().getSqlSegment().contains("active"));
    }

    /** 统一 ID 不能让普通提交详情绕过比赛或验题入口。 */
    @Test
    void regularDetailRejectsContestAndReviewRecords() {
        SubmissionServiceImpl regular = new SubmissionServiceImpl(submissions, mock(UserMapper.class),
                problems, datasets, tasks, validator, mock(SysProperties.class), mock(PageFactory.class));
        ReflectionTestUtils.setField(regular, "baseMapper", submissions);
        Submission submission = new Submission();
        submission.setId(42L);
        when(submissions.selectById(42L)).thenReturn(submission);
        for (String type : new String[]{SubmissionType.CONTEST, SubmissionType.PROBLEM_REVIEW}) {
            submission.setSubmissionType(type);
            assertThrows(BaseException.class, () -> regular.getSubmissionById(42L));
        }
        verifyNoInteractions(problems, datasets);
    }

    /** 验题详情仅返回验题记录，管理员字段仍表示真实提交人。 */
    @Test
    void reviewDetailPreservesReviewerAndRejectsOtherTypes() {
        Submission submission = new Submission();
        submission.setId(42L);
        submission.setUserId(9L);
        submission.setSubmissionType(SubmissionType.PROBLEM_REVIEW);
        when(submissions.selectById(42L)).thenReturn(submission);
        assertEquals(9L, reviews.getReviewSubmission(42L).getReviewerId());
        submission.setSubmissionType(SubmissionType.REGULAR);
        assertThrows(BaseException.class, () -> reviews.getReviewSubmission(42L));
    }
}
