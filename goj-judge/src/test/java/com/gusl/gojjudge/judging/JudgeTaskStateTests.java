package com.gusl.gojjudge.judging;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gusl.common.constant.JudgeTaskStatus;
import com.gusl.common.constant.JudgeTaskType;
import com.gusl.common.pojo.entity.JudgeTask;
import com.gusl.common.pojo.entity.JudgeTaskMessage;
import com.gusl.gojjudge.judging.JudgeService;
import com.gusl.gojjudge.judging.result.SubmissionResultService;
import com.gusl.gojjudge.task.JudgeTaskStateService;
import com.gusl.gojjudge.task.mapper.JudgeTaskMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证任务状态收拢后，重试、死亡、成功补偿及过期保护保持原有边界。 */
class JudgeTaskStateTests {
    private final JudgeTaskMapper tasks = mock(JudgeTaskMapper.class);
    private final JudgeService judge = mock(JudgeService.class);
    private final SubmissionResultService results = mock(SubmissionResultService.class);
    private final JudgeTaskStateService service = new JudgeTaskStateService(tasks, judge, results);
    private final JudgeTask task = new JudgeTask();
    private final JudgeTaskMessage message = new JudgeTaskMessage();

    /** 初始化当前 Worker 持有的任务及 MyBatis 字段映射。 */
    @BeforeEach
    void prepare() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), JudgeTask.class);
        ReflectionTestUtils.setField(service, "workerName", "worker-a");
        ReflectionTestUtils.setField(service, "retryDelaySeconds", 10L);
        task.setId(1L);
        task.setTaskType(JudgeTaskType.SUBMISSION);
        task.setBusinessId(2L);
        task.setStatus(JudgeTaskStatus.PROCESSING);
        task.setLeaseOwner("worker-a");
        task.setAttemptCount(1);
        task.setMaxAttempts(3);
        task.setLeaseExpireTime(LocalDateTime.now().minusMinutes(1));
        message.setTaskId(1L);
        message.setTaskType(task.getTaskType());
        message.setBusinessId(2L);
        when(tasks.selectById(1L)).thenReturn(task);
        when(tasks.update(any())).thenReturn(1);
    }

    @Test
    void retryDoesNotWriteBusinessSystemError() {
        service.handleFailure(message, new RuntimeException("沙箱不可用"));
        assertTrue(update().getParamNameValuePairs().containsValue(JudgeTaskStatus.RETRY_WAIT));
        verifyNoInteractions(results);
    }

    @Test
    void exhaustedAttemptsWriteDeadThenBusinessError() {
        task.setAttemptCount(3);
        service.handleFailure(message, new RuntimeException("沙箱不可用"));
        assertTrue(update().getParamNameValuePairs().containsValue(JudgeTaskStatus.DEAD));
        InOrder order = inOrder(tasks, results);
        order.verify(tasks).update(any());
        order.verify(results).markSystemError(JudgeTaskType.SUBMISSION, 2L, "沙箱不可用");
    }

    @Test
    void rejectedTaskTransitionDoesNotWriteBusinessError() {
        task.setAttemptCount(3);
        when(tasks.update(any())).thenReturn(0);
        service.handleFailure(message, new RuntimeException("故障"));
        verifyNoInteractions(results);
    }

    @Test
    void terminalBusinessCompletesTaskInsteadOfRetrying() {
        when(judge.isBusinessTerminal(task.getTaskType(), 2L)).thenReturn(true);
        service.handleFailure(message, new RuntimeException("任务状态写入中断"));
        assertTrue(update().getParamNameValuePairs().containsValue(JudgeTaskStatus.SUCCEEDED));
        verifyNoInteractions(results);
    }

    @Test
    void recoveryKeepsExpiryConditionWhenCompletingTask() {
        when(judge.isBusinessTerminal(task.getTaskType(), 2L)).thenReturn(true);
        service.recover(1L);
        LambdaUpdateWrapper<JudgeTask> update = update();
        assertTrue(update.getSqlSegment().contains("lease_expire_time <="));
        assertTrue(update.getParamNameValuePairs().containsValue(JudgeTaskStatus.SUCCEEDED));
    }

    @Test
    void recoverySkipsRenewedLease() {
        task.setLeaseExpireTime(LocalDateTime.now().plusMinutes(1));
        service.recover(1L);
        verify(tasks, never()).update(any());
        verifyNoInteractions(judge, results);
    }

    @Test
    void failureFromAnotherWorkerIsIgnored() {
        task.setLeaseOwner("worker-b");
        service.handleFailure(message, new RuntimeException("过期执行"));
        verify(tasks, never()).update(any());
        verifyNoInteractions(judge, results);
    }

    /** 读取实际 SQL 条件和参数，确认没有把不同任务状态混淆。 */
    private LambdaUpdateWrapper<JudgeTask> update() {
        ArgumentCaptor<LambdaUpdateWrapper<JudgeTask>> capture = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(tasks).update(capture.capture());
        capture.getValue().getSqlSegment();
        return capture.getValue();
    }
}
