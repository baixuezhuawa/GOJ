package com.gusl.gojserver.submission;

import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.gusl.common.common.BaseException;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.constant.SubmissionType;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojserver.shared.infrastructure.config.properties.SubmissionCacheProperties;
import com.gusl.gojserver.submission.application.SubmissionStatusService;
import com.gusl.gojserver.submission.application.model.SubmissionStatusSnapshot;
import com.gusl.gojserver.submission.infrastructure.cache.SubmissionStatusCache;
import com.gusl.gojserver.submission.infrastructure.canal.SubmissionCanalConsumer;
import com.gusl.gojserver.submission.infrastructure.canal.SubmissionSyncHealth;
import com.gusl.gojserver.submission.infrastructure.persistence.SubmissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 提交状态测试，覆盖回源、归属隔离、重放及回填并发。 */
class SubmissionStatusTests {
    private SubmissionMapper mapper;
    private SubmissionStatusCache cache;
    private SubmissionCacheProperties properties;
    private SubmissionSyncHealth health;
    private SubmissionStatusService service;

    /** 构造真实状态服务，以内存替身隔离外部连接。 */
    @BeforeEach
    void setup() {
        mapper = mock(SubmissionMapper.class);
        cache = mock(SubmissionStatusCache.class);
        properties = new SubmissionCacheProperties();
        properties.setEnabled(true);
        health = new SubmissionSyncHealth(properties);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        service = new SubmissionStatusService(mapper, cache, properties, health, transactions);
    }

    /** 健康且近期核验的缓存命中不查库、不续期。 */
    @Test
    void freshHitDoesNotQueryOrWrite() {
        health.success(0);
        when(cache.get(1L)).thenReturn(snapshot());
        assertEquals(JudgingConstant.RUNNING, service.getStatus(1L, SubmissionType.REGULAR, null, false).getStatus());
        verifyNoInteractions(mapper);
        verify(cache, never()).put(any(), any());
    }

    /** Canal 静默失效也能通过核验期限发现终态。 */
    @Test
    void staleSnapshotRechecksDatabase() {
        health.success(0);
        SubmissionStatusSnapshot old = snapshot();
        old.setVerifiedAt(1);
        when(cache.get(1L)).thenReturn(old);
        when(mapper.findStatus(1L)).thenReturn(submission(JudgingConstant.ACCEPTED, 1));
        assertEquals(JudgingConstant.ACCEPTED, service.getStatus(1L, SubmissionType.REGULAR, null, false).getStatus());
    }

    /** 缓存命中也不能跨普通、比赛和不同比赛入口访问。 */
    @Test
    void cachedOwnershipIsChecked() {
        health.success(0);
        SubmissionStatusSnapshot cached = snapshot();
        cached.setSubmissionType(SubmissionType.CONTEST);
        cached.setContestId(8L);
        when(cache.get(1L)).thenReturn(cached);
        assertThrows(BaseException.class, () -> service.getStatus(1L, SubmissionType.REGULAR, null, false));
        assertThrows(BaseException.class, () -> service.getStatus(1L, SubmissionType.CONTEST, 9L, false));
        assertNotNull(service.getStatus(1L, SubmissionType.CONTEST, 8L, false));
    }

    /** 重判和历史事件都读取当前主库，null 字段完整覆盖。 */
    @Test
    void replayUsesCurrentGenerationAndClearsOldFields() {
        SubmissionStatusSnapshot old = snapshot();
        old.setStatus(JudgingConstant.ACCEPTED);
        old.setTimeMs(50);
        old.setCompilerMsg("旧编译输出");
        when(cache.get(1L)).thenReturn(old);
        when(mapper.findStatus(1L)).thenReturn(submission(JudgingConstant.IN_QUEUE, 2));
        service.synchronize(1L);
        ArgumentCaptor<SubmissionStatusSnapshot> written = ArgumentCaptor.forClass(SubmissionStatusSnapshot.class);
        verify(cache).put(written.capture(), eq(old));
        assertEquals(2, written.getValue().getVersion());
        assertNull(written.getValue().getTimeMs());
        assertNull(written.getValue().getCompilerMsg());
    }

    /** 写缓存失败时页面仍返回主库结果，但 CDC 必须失败以便重试。 */
    @Test
    void redisFailureDegradesReadsButFailsConsumption() {
        when(cache.get(1L)).thenThrow(new IllegalStateException("Redis 不可用"));
        when(mapper.findStatus(1L)).thenReturn(submission(JudgingConstant.ACCEPTED, 1));
        assertEquals(JudgingConstant.ACCEPTED, service.getStatus(1L, SubmissionType.REGULAR, null, false).getStatus());
        assertThrows(IllegalStateException.class, () -> service.synchronize(1L));
    }

    /** 已删除记录和管理员验题只清理缓存。 */
    @Test
    void deletedAndReviewRowsAreNotCached() {
        service.synchronize(1L);
        Submission review = submission(JudgingConstant.ACCEPTED, 1);
        review.setSubmissionType(SubmissionType.PROBLEM_REVIEW);
        when(mapper.findStatus(1L)).thenReturn(review);
        service.synchronize(1L);
        verify(cache, times(2)).delete(1L);
        verify(cache, never()).put(any(), any());
    }

    /** CDC 必须等待同一 ID 的回填完成，之后重新读取更新后的主库。 */
    @Test
    void refillCannotFinishAfterNewerCdcWrite() throws Exception {
        CountDownLatch firstRead = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(mapper.findStatus(1L)).thenAnswer(invocation -> {
            firstRead.countDown();
            assertTrue(release.await(3, TimeUnit.SECONDS));
            return submission(JudgingConstant.RUNNING, 1);
        }).thenReturn(submission(JudgingConstant.ACCEPTED, 1));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> refill = executor.submit(() -> service.getStatus(1L, SubmissionType.REGULAR, null, true));
            assertTrue(firstRead.await(3, TimeUnit.SECONDS));
            Future<?> event = executor.submit(() -> service.synchronize(1L));
            release.countDown();
            refill.get(3, TimeUnit.SECONDS);
            event.get(3, TimeUnit.SECONDS);
            ArgumentCaptor<SubmissionStatusSnapshot> writes = ArgumentCaptor.forClass(SubmissionStatusSnapshot.class);
            verify(cache, times(2)).put(writes.capture(), any());
            assertEquals(JudgingConstant.ACCEPTED, writes.getAllValues().get(1).getStatus());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    /** 一批中相同 ID 去重，删除使用 beforeColumns，其他表不触发刷新。 */
    @Test
    void canalParsesInsertUpdateDeleteAndDeduplicates() throws Exception {
        SubmissionStatusService target = mock(SubmissionStatusService.class);
        SubmissionCanalConsumer consumer = new SubmissionCanalConsumer(properties, target, health);
        consumer.process(new Message(7, List.of(event(CanalEntry.EventType.INSERT, 1),
                event(CanalEntry.EventType.UPDATE, 1), event(CanalEntry.EventType.DELETE, 2))));
        verify(target).synchronize(1L);
        verify(target).synchronize(2L);
        verifyNoMoreInteractions(target);
    }

    /** 消费处理失败向外传播，消费循环才会 rollback 而非 ack。 */
    @Test
    void failedRefreshIsNotSwallowed() {
        SubmissionStatusService target = mock(SubmissionStatusService.class);
        doThrow(new IllegalStateException("写入失败")).when(target).synchronize(1L);
        SubmissionCanalConsumer consumer = new SubmissionCanalConsumer(properties, target, health);
        assertThrows(IllegalStateException.class,
                () -> consumer.process(new Message(7, List.of(event(CanalEntry.EventType.UPDATE, 1)))));
    }

    /** 构造 Canal 行事件，确保测试经过真实 protobuf 解析。 */
    private CanalEntry.Entry event(CanalEntry.EventType type, long id) {
        CanalEntry.Column column = CanalEntry.Column.newBuilder().setName("id").setValue(String.valueOf(id)).build();
        CanalEntry.RowData.Builder row = CanalEntry.RowData.newBuilder();
        if (type == CanalEntry.EventType.DELETE) row.addBeforeColumns(column);
        else row.addAfterColumns(column);
        return CanalEntry.Entry.newBuilder().setEntryType(CanalEntry.EntryType.ROWDATA)
                .setHeader(CanalEntry.Header.newBuilder().setSchemaName("goj").setTableName("submission"))
                .setStoreValue(CanalEntry.RowChange.newBuilder().setEventType(type).addRowDatas(row).build().toByteString())
                .build();
    }

    /** 构造有效的普通提交缓存。 */
    private SubmissionStatusSnapshot snapshot() {
        SubmissionStatusSnapshot result = new SubmissionStatusSnapshot();
        result.setId(1L);
        result.setVersion(1);
        result.setSubmissionType(SubmissionType.REGULAR);
        result.setStatus(JudgingConstant.RUNNING);
        result.setVerifiedAt(System.currentTimeMillis());
        return result;
    }

    /** 构造当前数据库记录。 */
    private Submission submission(String status, int version) {
        Submission result = new Submission();
        result.setId(1L);
        result.setVersion(version);
        result.setSubmissionType(SubmissionType.REGULAR);
        result.setStatus(status);
        return result;
    }
}
