package com.gusl.gojserver.contest;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gusl.common.common.BaseException;
import com.gusl.common.common.PageQuery;
import com.gusl.common.constant.*;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojserver.account.domain.model.User;
import com.gusl.gojserver.account.infrastructure.persistence.UserMapper;
import com.gusl.gojserver.contest.application.model.*;
import com.gusl.gojserver.contest.application.ranking.*;
import com.gusl.gojserver.contest.application.scheduling.ContestTask;
import com.gusl.gojserver.contest.domain.ranking.RankCalculator;
import com.gusl.gojserver.contest.infrastructure.persistence.*;
import com.gusl.gojserver.contest.interfaces.web.ContestRankingController;
import com.gusl.gojserver.shared.application.pagination.PageFactory;
import com.gusl.gojserver.submission.infrastructure.persistence.SubmissionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 排行榜业务测试，使用内存数据验证增量计分、回放和阶段顺序，不连接外部服务。 */
class RankingTests {
    private final ContestMapper contests = mock(ContestMapper.class);
    private final ContestProblemMapper problems = mock(ContestProblemMapper.class);
    private final ContestParticipateMapper participants = mock(ContestParticipateMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final SubmissionMapper submissions = mock(SubmissionMapper.class);
    private final ContestSubmissionHistoryMapper histories = mock(ContestSubmissionHistoryMapper.class);
    private final ContestRankSnapshotMapper snapshots = mock(ContestRankSnapshotMapper.class);
    private final LocalDateTime start = LocalDateTime.of(2026, 10, 21, 10, 0);
    private final RankingManager manager = new RankingManager(contests, problems, participants, users, submissions);
    private final RankingArchiveService archive = new RankingArchiveService(contests, problems, participants,
            users, submissions, histories, snapshots);
    private final VpRankingService vp = new VpRankingService(contests, problems, histories, snapshots);
    private Contest contest;
    private List<ContestProblem> columns;

    /** 初始化 MyBatis Lambda 元数据和两位正式参赛者。 */
    @BeforeEach
    void prepare() {
        for (Class<?> type : new Class<?>[]{Contest.class, ContestProblem.class, ContestParticipate.class,
                User.class, Submission.class, ContestSubmissionHistory.class, ContestRankSnapshotRecord.class}) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
        contest = Contest.builder().id(1L).startTime(start).endTime(start.plusHours(3)).status(ContestStatus.RUNNING).build();
        when(contests.selectById(1L)).thenReturn(contest);
        when(contests.selectOne(any())).thenReturn(contest);
        ContestProblem a = new ContestProblem(); a.setProblemId(10L); a.setSortOrder(0);
        ContestProblem b = new ContestProblem(); b.setProblemId(20L); b.setSortOrder(1);
        columns = Arrays.asList(a, b);
        when(problems.selectList(any())).thenReturn(columns);
        ContestParticipate one = new ContestParticipate(); one.setUserId(1L);
        ContestParticipate two = new ContestParticipate(); two.setUserId(2L);
        when(participants.selectList(any())).thenReturn(Arrays.asList(one, two));
        when(users.selectList(any())).thenReturn(Arrays.asList(user(1L), user(2L)));
        when(submissions.selectList(any())).thenReturn(Collections.emptyList());
    }

    @Test
    void unorderedDuplicateAndRejudgeMessagesOnlyRecalculateOneProblemInMemory() {
        manager.initialize(1L);
        clearInvocations(contests, problems, participants, users, submissions);
        manager.applyResult(message(2, 1, 10, 20, JudgingConstant.ACCEPTED, 1));
        manager.applyResult(message(1, 1, 10, 10, JudgingConstant.WRONG_ANSWER, 1));
        manager.applyResult(message(1, 1, 10, 10, JudgingConstant.WRONG_ANSWER, 1));
        manager.applyResult(message(3, 1, 20, 30, JudgingConstant.ACCEPTED, 1));
        assertEquals(70, rows().get(0).getPenalty());
        manager.applyResult(message(1, 1, 10, 10, JudgingConstant.ACCEPTED, 2));
        manager.applyResult(message(1, 1, 10, 10, JudgingConstant.WRONG_ANSWER, 1));
        assertEquals(40, rows().get(0).getPenalty());
        manager.applyResult(message(1, 1, 10, 10, SystemConstant.SYSTEM_ERROR, 3));
        assertEquals(50, rows().get(0).getPenalty());
        manager.applyResult(message(2, 1, 10, 20, JudgingConstant.WRONG_ANSWER, 2));
        assertEquals(1, rows().get(0).getAcceptedCount());
        assertEquals(30, rows().get(0).getPenalty());
        verifyNoInteractions(contests, problems, participants, users, submissions);
    }

    @Test
    void registrationNotificationAfterResultRestoresOnlyItsMemoryAndSnapshotsAreDetached() {
        manager.initialize(1L);
        manager.applyResult(message(1, 3, 10, 5, JudgingConstant.ACCEPTED, 1));
        manager.addParticipant(1L, 3L, "user3");
        manager.addParticipant(1L, 3L, "user3");
        List<UserRankRow> rows = rows();
        assertEquals(3, rows.size());
        assertEquals(5, rows.get(0).getPenalty());
        rows.get(0).getStates().get(0).setAttempt(99);
        assertEquals(0, rows().get(0).getStates().get(0).getAttempt());
    }

    @Test
    void concurrentQueriesSeeConsistentTotalsAndMessagesAreDeduplicated() throws Exception {
        manager.initialize(1L);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                futures.add(executor.submit(() -> {
                    manager.applyResult(message(1, 1, 10, 5, JudgingConstant.ACCEPTED, 1));
                    UserRankRow row = rows().get(0);
                    assertEquals(1, row.getAcceptedCount());
                    assertEquals(5, row.getPenalty());
                    assertTrue(row.getStates().get(0).isAccepted());
                }));
            }
            for (Future<?> future : futures) { future.get(5, TimeUnit.SECONDS); }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void restoredSubmissionsSeedVersionIndexAndIdsDetermineFirstAcceptance() {
        // 时间相同仍按 ID 先后计罚，重启后的重复消息不能再次累加。
        when(submissions.selectList(any())).thenReturn(Arrays.asList(
                submission(1, 1, 10, 5, JudgingConstant.WRONG_ANSWER),
                submission(2, 1, 10, 5, JudgingConstant.ACCEPTED)));
        manager.initialize(1L);
        manager.applyResult(message(1, 1, 10, 5, JudgingConstant.WRONG_ANSWER, 1));
        assertEquals(25, rows().get(0).getPenalty());
        verify(submissions, times(1)).selectList(any());
    }

    @Test
    void archiveWritesDenseHistoryBeforeZeroHundredAndFinalSnapshotsAndCanRetry() {
        contest.setStatus(ContestStatus.SYNC);
        List<Submission> source = new ArrayList<>();
        for (int i = 1; i <= 101; i++) {
            source.add(submission(i * 7, 1, 10, i, i == 101 ? JudgingConstant.ACCEPTED : JudgingConstant.WRONG_ANSWER));
        }
        when(submissions.selectList(any())).thenReturn(source);
        List<ContestSubmissionHistory> stored = new ArrayList<>();
        doAnswer(call -> { stored.clear(); return 1; }).when(histories).delete(any());
        doAnswer(call -> { stored.add(call.getArgument(0)); return 1; }).when(histories).insert(any(ContestSubmissionHistory.class));
        when(histories.selectList(any())).thenAnswer(call -> new ArrayList<>(stored));
        archive.archive(1L);
        assertEquals(101, stored.size());
        assertEquals(1L, stored.get(0).getSequenceNo());
        assertEquals(7L, stored.get(0).getSubmissionId());
        ArgumentCaptor<ContestRankSnapshotRecord> capture = ArgumentCaptor.forClass(ContestRankSnapshotRecord.class);
        verify(snapshots, times(3)).insert(capture.capture());
        assertEquals(Arrays.asList(0L, 100L, 101L), Arrays.asList(capture.getAllValues().get(0).getLastSequence(),
                capture.getAllValues().get(1).getLastSequence(), capture.getAllValues().get(2).getLastSequence()));
        UserRankRow finalRow = JSONUtil.toList(capture.getAllValues().get(2).getRowsJson(), UserRankRow.class).get(0);
        assertEquals(2101, finalRow.getPenalty());
        assertEquals(100, finalRow.getStates().get(0).getAttempt());
        archive.archive(1L);
        assertEquals(101, stored.size());
    }

    @Test
    void unfinishedSubmissionPreventsArchiveAndLeavesExistingDataUntouched() {
        contest.setStatus(ContestStatus.SYNC);
        when(submissions.selectList(any())).thenReturn(Collections.singletonList(submission(1, 1, 10, 1, JudgingConstant.RUNNING)));
        assertThrows(BaseException.class, () -> archive.archive(1L));
        verifyNoInteractions(histories, snapshots);
    }

    @Test
    void vpReplacesOriginalUserAndRefreshAdvancesHistoryIncludingEqualTime() {
        contest.setStatus(ContestStatus.FINISH);
        LiveRankings base = new LiveRankings(contest, Arrays.asList(user(1L), user(2L)), columns);
        base.applyOrderedResult(1L, 10L, JudgingConstant.ACCEPTED, 1);
        ContestRankSnapshotRecord snapshot = new ContestRankSnapshotRecord();
        snapshot.setLastSequence(100L); snapshot.setRowsJson(JSONUtil.toJsonStr(base.copyRows()));
        when(snapshots.selectOne(any())).thenReturn(snapshot);
        ContestSubmissionHistory cutoff = history(102, 2, 20, 10, JudgingConstant.ACCEPTED);
        when(histories.selectOne(any())).thenReturn(cutoff);
        when(histories.selectList(any())).thenReturn(Arrays.asList(
                history(101, 1, 20, 9, JudgingConstant.ACCEPTED), cutoff));
        LocalDateTime vpStart = start.plusDays(1);
        Submission vpSubmission = submission(999, 1, 10, 5, JudgingConstant.WRONG_ANSWER);
        vpSubmission.setSubmissionTime(vpStart.plusMinutes(5));
        List<UserRankRow> rows = vp.replay(1L, 1L, "vp-user", vpStart, vpStart.plusMinutes(10), Collections.singletonList(vpSubmission));
        assertEquals(2L, rows.get(0).getUserId());
        UserRankRow mine = rows.get(1);
        assertEquals(0, mine.getAcceptedCount());
        assertEquals(1, mine.getStates().get(0).getAttempt());
        assertFalse(mine.getStates().get(1).isAccepted());
        assertEquals(1, JSONUtil.toList(snapshot.getRowsJson(), UserRankRow.class).stream()
                .filter(row -> row.getUserId().equals(1L)).findFirst().orElseThrow().getAcceptedCount());
        // 检查查询截止条件包含刷新时刻，而非最后一次 VP 提交时刻。
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<ContestSubmissionHistory>> query = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(histories).selectOne(query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("elapsed_millis <="));
    }

    @Test
    void tiesAndProblemOrderUseSharedRules() {
        LiveRankings ranking = new LiveRankings(contest, Arrays.asList(user(1L), user(2L), user(3L)), columns);
        ranking.applyOrderedResult(1L, 20L, JudgingConstant.ACCEPTED, 5);
        ranking.applyOrderedResult(2L, 10L, JudgingConstant.ACCEPTED, 5);
        List<UserRankRow> rows = ranking.copyRows();
        RankCalculator.sortAndRank(rows);
        assertEquals(1, rows.get(0).getRank());
        assertEquals(1, rows.get(1).getRank());
        assertEquals(3, rows.get(2).getRank());
        assertTrue(rows.get(0).getStates().get(1).isAccepted());
    }

    @Test
    void lifecycleWaitsThenArchivesBeforePublishingProblemsAndFinishing() {
        RankingArchiveService archiver = mock(RankingArchiveService.class);
        ContestTask task = new ContestTask(contests, problems, submissions, manager, archiver);
        when(contests.selectList(any())).thenReturn(Collections.singletonList(contest));
        contest.setStatus(ContestStatus.WAITING);
        when(submissions.selectCount(any())).thenReturn(1L);
        task.ending();
        verify(contests, never()).update(any());
        verifyNoInteractions(archiver);
        when(submissions.selectCount(any())).thenReturn(0L);
        task.ending();
        verify(contests).update(any());
        contest.setStatus(ContestStatus.SYNC);
        clearInvocations(contests);
        task.ending();
        org.mockito.InOrder order = inOrder(archiver, problems, contests);
        order.verify(archiver).archive(1L);
        order.verify(problems).update(any());
        order.verify(contests).update(any());
    }


    @Test
    void archiveFailureDoesNotPublishProblemsOrAdvanceStage() {
        RankingArchiveService archiver = mock(RankingArchiveService.class);
        ContestTask task = new ContestTask(contests, problems, submissions, manager, archiver);
        contest.setStatus(ContestStatus.SYNC);
        when(contests.selectList(any())).thenReturn(Collections.singletonList(contest));
        doThrow(new IllegalStateException("归档失败")).when(archiver).archive(1L);
        task.ending();
        verify(contests, never()).update(any());
        verify(problems, never()).update(any());
    }

    @Test
    void emptyContestStillArchivesAnInitialSnapshot() {
        contest.setStatus(ContestStatus.SYNC);
        when(histories.selectList(any())).thenReturn(Collections.emptyList());
        archive.archive(1L);
        ArgumentCaptor<ContestRankSnapshotRecord> capture = ArgumentCaptor.forClass(ContestRankSnapshotRecord.class);
        verify(snapshots).insert(capture.capture());
        assertEquals(0L, capture.getValue().getLastSequence());
        assertEquals(2, JSONUtil.toList(capture.getValue().getRowsJson(), UserRankRow.class).size());
    }

    @Test
    void cacheOnlyRebuildsForVisibleChangesAndPagesKeepGlobalRanks() {
        LiveRankings live = observedRanking();
        manager.getRankingPage(1L, new Page<>(1, 1));
        Page<UserRankRow> second = new Page<>(2, 1);
        manager.getRankingPage(1L, second);
        assertEquals(2, second.getTotal());
        assertEquals(1, second.getRecords().get(0).getRank());
        verify(live, times(1)).copyRows();

        manager.applyResult(message(1, 1, 10, 5, JudgingConstant.WRONG_ANSWER, 1));
        assertEquals(1, rows().get(0).getStates().get(0).getAttempt());
        verify(live, times(2)).copyRows();
        manager.applyResult(message(1, 1, 10, 5, JudgingConstant.WRONG_ANSWER, 1));
        rows();
        verify(live, times(2)).copyRows();

        manager.applyResult(message(2, 1, 10, 10, JudgingConstant.ACCEPTED, 1));
        rows();
        manager.applyResult(message(3, 1, 10, 20, JudgingConstant.WRONG_ANSWER, 1));
        rows();
        verify(live, times(3)).copyRows();
        // AC 后的提交虽然没有使缓存失效，但必须保留供重测使用。
        manager.applyResult(message(2, 1, 10, 10, JudgingConstant.WRONG_ANSWER, 2));
        assertEquals(3, rows().get(0).getStates().get(0).getAttempt());
        manager.addParticipant(1L, 3L, "user3");
        assertEquals(3, rows().size());
        manager.removeParticipant(1L, 3L);
        assertEquals(2, rows().size());
        verify(live, times(6)).copyRows();

        Page<UserRankRow> outside = new Page<>(Long.MAX_VALUE, Long.MAX_VALUE);
        manager.getRankingPage(1L, outside);
        assertTrue(outside.getRecords().isEmpty());
        assertEquals(2, outside.getTotal());
    }

    @Test
    void concurrentCacheMissesOnlyCopyTheFullRankingOnce() throws Exception {
        LiveRankings live = observedRanking();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch startQueries = new CountDownLatch(1);
        try {
            List<Future<?>> queries = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                queries.add(executor.submit(() -> {
                    await(startQueries);
                    assertEquals(2, rows().size());
                }));
            }
            startQueries.countDown();
            for (Future<?> query : queries) {
                query.get(5, TimeUnit.SECONDS);
            }
            verify(live, times(1)).copyRows();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void updateDuringSortingKeepsCopiedVersionAndNextQueryRefreshes() throws Exception {
        LiveRankings live = observedRanking();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch sorting = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        try {
            Future<List<UserRankRow>> query = executor.submit(() -> queryWithPausedSort(sorting, resume));
            assertTrue(sorting.await(5, TimeUnit.SECONDS));
            // 排序未结束时更新就能完成，证明排序没有持有榜单读锁。
            executor.submit(() -> manager.applyResult(message(1, 1, 10, 5, JudgingConstant.ACCEPTED, 1)))
                    .get(5, TimeUnit.SECONDS);
            resume.countDown();
            assertEquals(0, query.get(5, TimeUnit.SECONDS).get(0).getAcceptedCount());
            assertEquals(1, rows().get(0).getAcceptedCount());
            verify(live, times(2)).copyRows();
        } finally {
            resume.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void closeDuringSortingDiscardsCacheAndLateMessagesDoNotRestoreRanking() throws Exception {
        observedRanking();
        Object oldContext = contexts().get(1L);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch sorting = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        try {
            Future<List<UserRankRow>> query = executor.submit(() -> queryWithPausedSort(sorting, resume));
            assertTrue(sorting.await(5, TimeUnit.SECONDS));
            contest.setStatus(ContestStatus.SYNC);
            executor.submit(() -> manager.close(1L)).get(5, TimeUnit.SECONDS);
            resume.countDown();
            assertNull(query.get(5, TimeUnit.SECONDS));
            assertTrue(contexts().isEmpty());
            assertEquals(true, ReflectionTestUtils.getField(oldContext, "closed"));
            assertNull(ReflectionTestUtils.getField(oldContext, "cachedRows"));
            assertNull(ReflectionTestUtils.getField(oldContext, "ranking"));
            assertTrue(((Map<?, ?>) ReflectionTestUtils.getField(oldContext, "results")).isEmpty());
            clearInvocations(problems, participants, users, submissions);
            manager.applyResult(message(1, 1, 10, 5, JudgingConstant.ACCEPTED, 1));
            manager.addParticipant(1L, 3L, "user3");
            assertNull(rows());
            assertTrue(contexts().isEmpty());
            verifyNoInteractions(problems, participants, users, submissions);
        } finally {
            resume.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void restartedServerIgnoresSyncAndFinishedMessagesWithoutLoadingContestData() {
        for (String status : Arrays.asList(ContestStatus.SYNC, ContestStatus.FINISH)) {
            contest.setStatus(status);
            manager.applyResult(message(1, 1, 10, 5, JudgingConstant.ACCEPTED, 1));
            assertTrue(contexts().isEmpty());
        }
        verifyNoInteractions(problems, participants, users, submissions);
    }

    @Test
    void closeWaitsForInFlightResultUpdateBeforeReleasingMemory() throws Exception {
        LiveRankings live = observedRanking();
        CountDownLatch updating = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        CountDownLatch closing = new CountDownLatch(1);
        doAnswer(call -> {
            updating.countDown();
            await(resume);
            return call.callRealMethod();
        }).when(live).updateProblemState(any(), any(), any());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> update = executor.submit(() -> manager.applyResult(message(1, 1, 10, 5, JudgingConstant.ACCEPTED, 1)));
            assertTrue(updating.await(5, TimeUnit.SECONDS));
            contest.setStatus(ContestStatus.SYNC);
            Future<?> close = executor.submit(() -> {
                closing.countDown();
                manager.close(1L);
            });
            assertTrue(closing.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> close.get(100, TimeUnit.MILLISECONDS));
            resume.countDown();
            update.get(5, TimeUnit.SECONDS);
            close.get(5, TimeUnit.SECONDS);
            assertTrue(contexts().isEmpty());
            assertNull(rows());
        } finally {
            resume.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void waitingOnlyClosesAfterSuccessfulTransitionAndSyncClosesBeforeArchive() {
        RankingManager lifecycleManager = mock(RankingManager.class);
        RankingArchiveService archiver = mock(RankingArchiveService.class);
        ContestTask task = new ContestTask(contests, problems, submissions, lifecycleManager, archiver);
        contest.setStatus(ContestStatus.WAITING);
        when(contests.selectList(any())).thenReturn(Collections.singletonList(contest));
        when(submissions.selectCount(any())).thenReturn(1L, 0L, 0L);
        when(contests.update(any())).thenReturn(0, 1);
        task.ending();
        task.ending();
        verifyNoInteractions(lifecycleManager);
        task.ending();
        verify(lifecycleManager).close(1L);
        contest.setStatus(ContestStatus.SYNC);
        clearInvocations(lifecycleManager, contests);
        task.ending();
        org.mockito.InOrder order = inOrder(lifecycleManager, archiver, contests);
        order.verify(lifecycleManager).close(1L);
        order.verify(archiver).archive(1L);
        order.verify(contests).update(any());
    }

    @Test
    void controllerReportsSyncAndFallsBackToFinalArchiveWhenContextCloses() {
        RankingManager liveManager = mock(RankingManager.class);
        RankingArchiveService archiver = mock(RankingArchiveService.class);
        PageFactory pages = mock(PageFactory.class);
        ContestRankingController controller = new ContestRankingController(liveManager, archiver, contests, problems, pages);
        PageQuery query = new PageQuery();
        contest.setStatus(ContestStatus.SYNC);
        assertEquals("排行榜正在整理", assertThrows(BaseException.class,
                () -> controller.getRanking(1L, query)).getMessage());
        verifyNoInteractions(liveManager, archiver);

        Contest waiting = Contest.builder().id(1L).status(ContestStatus.WAITING).build();
        Contest finished = Contest.builder().id(1L).status(ContestStatus.FINISH).build();
        when(contests.selectById(1L)).thenReturn(waiting, contest, waiting, finished);
        when(pages.create(query)).thenAnswer(call -> new Page<>(1, 20));
        assertEquals("排行榜正在整理", assertThrows(BaseException.class,
                () -> controller.getRanking(1L, query)).getMessage());
        when(archiver.getFinalRankings(1L)).thenReturn(Collections.emptyList());
        assertNotNull(controller.getRanking(1L, query));
        verify(archiver).getFinalRankings(1L);
    }

    /** 查询完整测试样本，生产接口仅查询所需页。 */
    private List<UserRankRow> rows() {
        Page<UserRankRow> page = new Page<>(1, 100);
        return manager.getRankingPage(1L, page) == null ? null : page.getRecords();
    }

    /** 使用 Spring 测试反射获取上下文，检查清理后不再持有榜单。 */
    private Map<?, ?> contexts() {
        return (Map<?, ?>) ReflectionTestUtils.getField(manager, "rankings");
    }

    /** 观察整榜复制次数，避免通过耗时猜测是否命中缓存。 */
    private LiveRankings observedRanking() {
        manager.initialize(1L);
        Object context = contexts().get(1L);
        LiveRankings live = spy((LiveRankings) ReflectionTestUtils.getField(context, "ranking"));
        ReflectionTestUtils.setField(context, "ranking", live);
        return live;
    }

    /** Mockito 静态替身仅在查询线程暂停排序，复现排序期间更新或关闭。 */
    private List<UserRankRow> queryWithPausedSort(CountDownLatch sorting, CountDownLatch resume) {
        try (MockedStatic<RankCalculator> calculator = mockStatic(RankCalculator.class, CALLS_REAL_METHODS)) {
            calculator.when(() -> RankCalculator.sortAndRank(any())).thenAnswer(call -> {
                sorting.countDown();
                await(resume);
                return call.callRealMethod();
            });
            return rows();
        }
    }

    /** 限时等待并发步骤，避免测试失败后永久挂起。 */
    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("并发测试被中断", exception);
        }
    }

    /** 创建参赛者展示资料。 */
    private User user(Long id) { User user = new User(); user.setId(id); user.setUsername("user" + id); return user; }
    /** 创建当前提交的结果消息。 */
    private ContestJudgeResultMessage message(long id, long user, long problem, int minute, String status, int version) {
        return new ContestJudgeResultMessage(1L, problem, user, id, version, status, start.plusMinutes(minute));
    }
    /** 创建统一提交实体。 */
    private Submission submission(long id, long user, long problem, int minute, String status) {
        Submission result = new Submission(); result.setId(id); result.setContestId(1L); result.setUserId(user);
        result.setProblemId(problem); result.setVersion(1); result.setStatus(status); result.setSubmissionTime(start.plusMinutes(minute));
        return result;
    }
    /** 创建已离散化的历史提交。 */
    private ContestSubmissionHistory history(long seq, long user, long problem, int minute, String status) {
        ContestSubmissionHistory result = new ContestSubmissionHistory(); result.setSequenceNo(seq); result.setUserId(user);
        result.setProblemId(problem); result.setElapsedMillis(minute * 60000L); result.setStatus(status); return result;
    }
}
