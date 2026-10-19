package com.gusl.gojjudge.task;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gusl.common.constant.JudgeTaskStatus;
import com.gusl.common.constant.JudgeTaskType;
import com.gusl.common.pojo.entity.JudgeTask;
import com.gusl.common.pojo.entity.JudgeTaskMessage;
import com.gusl.gojjudge.judging.JudgeService;
import com.gusl.gojjudge.judging.result.SubmissionResultService;
import com.gusl.gojjudge.task.mapper.JudgeTaskMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证公开入口和事务完成行为，不将 Mock 当作真实数据库锁测试。 */
class JudgeTaskProcessorTests {
    private final JudgeService judge = mock(JudgeService.class);
    private final JudgeTaskMapper tasks = mock(JudgeTaskMapper.class);
    private final SubmissionResultService results = mock(SubmissionResultService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    private final JudgeTaskProcessor processor = new JudgeTaskProcessor(judge, tasks, results,
            new TransactionTemplate(transactions));
    private final JudgeTaskMessage message = new JudgeTaskMessage();
    private final JudgeTask task = new JudgeTask();

    /** 使用真实 TransactionTemplate，观察事务管理器收到提交或回滚。 */
    @BeforeEach
    void prepare() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), JudgeTask.class);
        ReflectionTestUtils.setField(processor, "workerName", "worker-a");
        ReflectionTestUtils.setField(processor, "processingTimeoutMinutes", 10L);
        ReflectionTestUtils.setField(processor, "retryDelaySeconds", 10L);
        message.setTaskId(1L);
        message.setTaskType(JudgeTaskType.SUBMISSION);
        message.setBusinessId(2L);
        message.setTaskVersion(1);
        task.setId(1L);
        task.setTaskType(JudgeTaskType.SUBMISSION);
        task.setBusinessId(2L);
        task.setAttemptCount(1);
        task.setMaxAttempts(3);
        when(tasks.update(any())).thenReturn(1);
        when(tasks.selectOne(any())).thenReturn(task);
        when(transactions.getTransaction(any())).thenReturn(transaction);
    }

    @Test
    void duplicateOrOutdatedMessageDoesNotRunJudge() {
        when(tasks.update(any())).thenReturn(0);
        processor.process(message);
        verifyNoInteractions(judge, results, transactions);
        verify(tasks, never()).selectOne(any());
        LambdaUpdateWrapper<JudgeTask> claim = updates(1).get(0);
        assertTrue(claim.getSqlSegment().contains("task_version"));
        assertTrue(claim.getSqlSegment().contains("business_id"));
        assertTrue(claim.getSqlSegment().contains("next_retry_time <="));
        assertTrue(claim.getSqlSet().contains("attempt_count = attempt_count + 1"));
    }

    @Test
    void successfulJudgeRunsOutsideTaskTransaction() {
        processor.process(message);
        InOrder order = inOrder(judge, transactions, tasks);
        order.verify(tasks).update(any());
        order.verify(judge).judge(message);
        order.verify(transactions).getTransaction(any());
        order.verify(tasks).selectOne(any());
        order.verify(tasks).update(any());
        order.verify(transactions).commit(transaction);
        assertEquals(JudgeTaskStatus.SUCCEEDED, assignment(updates(2).get(1), "status"));
        assertNull(assignment(updates(2).get(1), "lease_owner"));
        verify(judge, never()).isBusinessTerminal(any(), any());
        assertTrue(query().getSqlSegment().contains("lease_owner ="));
        assertTrue(query().getSqlSegment().endsWith("FOR UPDATE"));
    }

    @Test
    void failureSchedulesDelayedRetry() {
        doThrow(new IllegalStateException("沙箱不可用")).when(judge).judge(message);
        LocalDateTime before = LocalDateTime.now();
        processor.process(message);
        LambdaUpdateWrapper<JudgeTask> update = updates(2).get(1);
        assertEquals(JudgeTaskStatus.RETRY_WAIT, assignment(update, "status"));
        assertEquals("沙箱不可用", assignment(update, "last_error"));
        assertFalse(((LocalDateTime) assignment(update, "next_retry_time")).isBefore(before.plusSeconds(10)));
        verifyNoInteractions(results);
        verify(transactions).commit(transaction);
    }

    @Test
    void completedBusinessRecoversAsSuccessAfterException() {
        doThrow(new IllegalStateException("任务收尾中断")).when(judge).judge(message);
        when(judge.isBusinessTerminal(JudgeTaskType.SUBMISSION, 2L)).thenReturn(true);
        processor.process(message);
        assertEquals(JudgeTaskStatus.SUCCEEDED, assignment(updates(2).get(1), "status"));
        verifyNoInteractions(results);
    }

    @Test
    void lostLeaseDoesNotWriteTaskOrSubmission() {
        when(tasks.selectOne(any())).thenReturn(null);
        processor.process(message);
        verify(tasks, times(1)).update(any());
        verifyNoInteractions(results);
    }

    @Test
    void unexpiredOrFinishedTaskIsNotRecovered() {
        when(tasks.selectOne(any())).thenReturn(null);
        processor.recover(1L);
        verify(tasks, never()).update(any());
        verifyNoInteractions(judge, results);
        assertTrue(query().getSqlSegment().contains("lease_expire_time <="));
        assertTrue(query().getSqlSegment().contains("status ="));
        assertTrue(query().getSqlSegment().endsWith("FOR UPDATE"));
    }

    @Test
    void expiredLeaseRetriesImmediately() {
        LocalDateTime before = LocalDateTime.now();
        processor.recover(1L);
        LambdaUpdateWrapper<JudgeTask> update = updates(1).get(0);
        LocalDateTime retryTime = (LocalDateTime) assignment(update, "next_retry_time");
        assertEquals(JudgeTaskStatus.RETRY_WAIT, assignment(update, "status"));
        assertFalse(retryTime.isBefore(before));
        assertFalse(retryTime.isAfter(LocalDateTime.now()));
        assertNull(assignment(update, "last_dispatch_time"));
        verify(judge, never()).judge(any());
    }

    @Test
    void completedBusinessOnlyFinishesExpiredTask() {
        when(judge.isBusinessTerminal(JudgeTaskType.SUBMISSION, 2L)).thenReturn(true);
        processor.recover(1L);
        assertEquals(JudgeTaskStatus.SUCCEEDED, assignment(updates(1).get(0), "status"));
        verifyNoInteractions(results);
    }

    @Test
    void exhaustedAttemptsWriteSystemErrorInSameTransaction() {
        task.setAttemptCount(3);
        processor.recover(1L);
        InOrder order = inOrder(transactions, tasks, results);
        order.verify(transactions).getTransaction(any());
        order.verify(tasks).selectOne(any());
        order.verify(tasks).update(any());
        order.verify(results).markSystemError(JudgeTaskType.SUBMISSION, 2L, "Worker 租约过期");
        order.verify(transactions).commit(transaction);
        assertEquals(JudgeTaskStatus.DEAD, assignment(updates(1).get(0), "status"));
    }

    @Test
    void systemErrorWriteFailureRollsBackTaskTransition() {
        task.setAttemptCount(3);
        doThrow(new IllegalStateException("提交写回失败")).when(results)
                .markSystemError(JudgeTaskType.SUBMISSION, 2L, "Worker 租约过期");
        assertThrows(IllegalStateException.class, () -> processor.recover(1L));
        verify(transactions).rollback(transaction);
        verify(transactions, never()).commit(any());
    }

    @Test
    void failedTaskUpdateDoesNotWriteSystemError() {
        task.setAttemptCount(3);
        when(tasks.update(any())).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> processor.recover(1L));
        verifyNoInteractions(results);
        verify(transactions).rollback(transaction);
    }

    /** 提取捕获到的 MyBatis 更新条件。 */
    private List<LambdaUpdateWrapper<JudgeTask>> updates(int count) {
        ArgumentCaptor<LambdaUpdateWrapper<JudgeTask>> capture = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(tasks, times(count)).update(capture.capture());
        return capture.getAllValues();
    }

    /** 提取带行锁的任务查询。 */
    private LambdaQueryWrapper<JudgeTask> query() {
        ArgumentCaptor<LambdaQueryWrapper<JudgeTask>> capture = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(tasks).selectOne(capture.capture());
        return capture.getValue();
    }

    /** 根据 SQL 占位符读取字段赋值，不依赖固定参数序号。 */
    private Object assignment(LambdaUpdateWrapper<JudgeTask> update, String column) {
        for (String expression : update.getSqlSet().split(",")) {
            if (expression.startsWith(column + "=")) {
                String key = expression.substring(expression.indexOf("MPGENVAL"), expression.indexOf('}'));
                return update.getParamNameValuePairs().get(key);
            }
        }
        throw new AssertionError("没有更新字段：" + column);
    }
}
