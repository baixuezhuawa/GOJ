package com.gusl.gojserver.contest.application.ranking;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gusl.common.common.BaseException;
import com.gusl.common.constant.ContestStatus;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.constant.ParticipateType;
import com.gusl.common.constant.SubmissionType;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojserver.account.domain.model.User;
import com.gusl.gojserver.account.infrastructure.persistence.UserMapper;
import com.gusl.gojserver.contest.application.model.LiveRankings;
import com.gusl.gojserver.contest.application.model.UserRankRow;
import com.gusl.gojserver.contest.domain.ranking.RankCalculator;
import com.gusl.gojserver.contest.infrastructure.persistence.*;
import com.gusl.gojserver.submission.infrastructure.persistence.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/** 实时榜管理器，统一控制初始化、结果幂等、单场并发和查询副本。 */
@Component
@RequiredArgsConstructor
public class RankingManager {
    private final ContestMapper contests;
    private final ContestProblemMapper contestProblems;
    private final ContestParticipateMapper participants;
    private final UserMapper users;
    private final SubmissionMapper submissions;
    private final ConcurrentHashMap<Long, RankingContext> rankings = new ConcurrentHashMap<>();

    /** 单场上下文，读写锁保护榜单和缓存，重建锁只串行化查询缓存生成。 */
    private static class RankingContext {
        // JDK 读写锁允许多个查询同时读取，修改和关闭使用写锁。
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final Object rebuildLock = new Object();

        private boolean closed;

        private boolean initialized;

        /*
         * 排行榜缓存
         * 利用版本懒更新.
         * 每次增加更新排行榜的信息, 只是更新排行榜版本.
         * 如果版本不一致的时候, 才更新缓存.
         * 避免了在两次查询, 没有更新, 而都重新计算排行榜.
         */
        private long rankingVersion;
        private long cachedVersion = -1;
        private List<UserRankRow> cachedRows;

        private Contest contest;

        private LiveRankings ranking;

        private final Set<Long> problemIds = new HashSet<>();

        // 用户id, 题目id, SubmissionId, 判题结果
        private final Map<Long, Map<Long, TreeMap<Long, ContestJudgeResultMessage>>> results = new HashMap<>();
    }

    /** 首次加载比赛材料及已完成的提交，之后正常消息更新不访问数据库。 */
    public void initialize(Long contestId) {

        context(contestId);

    }

    // TODO 想要基于游标的形式, 分批处理, 避免出现异常, 出现数据不一致情况.

    /** 获取初始化成功的上下文；未完成时重新加载并构建。 */
    private RankingContext context(Long contestId) {
        RankingContext context = rankings.computeIfAbsent(contestId, id -> new RankingContext());

        // 已初始化的查询只获取读锁，避免缓存命中也争用写锁。
        context.lock.readLock().lock();
        try {
            if (context.closed) {
                return null;
            }
            if (context.initialized) {
                return context;
            }
        } finally {
            context.lock.readLock().unlock();
        }

        // 先释放读锁再获取写锁，重新检查初始化和关闭状态。
        context.lock.writeLock().lock();
        try {
            if (context.closed) {
                return null;
            }
            // 如果赛前已经初始化过就直接返回.
            if (context.initialized) {
                return context;
            }


            /* 如果是出现异常情况, context没有初始化成功, 获取需要另外计算context. */

            // 查询比赛
            Contest contest = contests.selectById(contestId);

            if (contest == null || ContestStatus.DRAFT.equals(contest.getStatus())) {
                throw new BaseException("比赛不存在或尚未发布");
            }
            // 清理后的迟到消息和服务重启都不能恢复已经进入归档阶段的实时榜。
            if (ContestStatus.SYNC.equals(contest.getStatus()) || ContestStatus.FINISH.equals(contest.getStatus())) {
                context.closed = true;
                rankings.remove(contestId, context);
                return null;
            }

            // 查询比赛题目
            List<ContestProblem> columns = contestProblems.selectList(
                    Wrappers.<ContestProblem>lambdaQuery()
                    .eq(ContestProblem::getContestId, contestId)
                    .orderByAsc(ContestProblem::getSortOrder)
            );

            // 获取用户
            List<User> registered = getParticipateUsers(contestId);

            // 获取
            List<Submission> completed = getCompletedSubmission(contest);

            // 失败重试先清理残留；写锁与 initialized 保证半成品不会被查询或报名操作使用。
            context.results.clear();
            context.problemIds.clear();
            context.rankingVersion = 0;
            context.cachedVersion = -1;
            context.cachedRows = null;

            context.contest = contest;
            context.ranking = new LiveRankings(contest, registered, columns);
            columns.forEach(problem -> context.problemIds.add(problem.getProblemId()));
            completed.forEach(submission -> apply(context, ContestJudgeResultMessage.from(submission)));

            // 全部恢复成功后才允许查询和报名操作使用榜单。
            context.initialized = true;

            return context;
        } finally {
            context.lock.writeLock().unlock();
        }
    }

    /** 接收单份结果，所有版本判断和结构更新均在该场比赛锁内完成。 */
    public void applyResult(ContestJudgeResultMessage message) {
        RankingContext context = context(message.getContestId());
        if (context == null) {
            return;
        }

        context.lock.writeLock().lock();

        try {
            if (!context.closed) {
                apply(context, message);
            }
        } finally {
            context.lock.writeLock().unlock();
        }
    }

    /** 拒绝重复或旧版本，按 ID 重算单题并由结构对象应用总成绩差额。 */
    private void apply(RankingContext context, ContestJudgeResultMessage message) {

        if (
                !JudgingConstant.TERMINAL_STATUSES.contains(message.getStatus())
                || !context.problemIds.contains(message.getProblemId())
                || message.getSubmissionTime().isBefore(context.contest.getStartTime())
                || !message.getSubmissionTime().isBefore(context.contest.getEndTime())
        ) {
            return;
        }

        TreeMap<Long, ContestJudgeResultMessage> resultMessages = context.results
                    .computeIfAbsent(message.getUserId(), id -> new HashMap<>())
                    .computeIfAbsent(message.getProblemId(), id -> new TreeMap<>());

        ContestJudgeResultMessage previous = resultMessages.get(message.getSubmissionId());

        if (previous != null && previous.getVersion() >= message.getVersion()) {
            return;
        }

        // 保存独立消息，避免调用方修改消息对象后破坏内部版本与结果。
        resultMessages.put(
                message.getSubmissionId(),
                new ContestJudgeResultMessage(
                        message.getContestId(),
                        message.getProblemId(),
                        message.getUserId(),
                        message.getSubmissionId(),
                        message.getVersion(),
                        message.getStatus(),
                        message.getSubmissionTime()
                )
        );

        if (context.ranking.updateProblemState(
                message.getUserId(),
                message.getProblemId(),
                RankCalculator.calculateProblem(resultMessages.values(), context.contest.getStartTime()))) {
            context.rankingVersion++;
        }
    }

    /** 报名成功后新增行；赛前尚未预热时交由初始化统一加载。 */
    public void addParticipant(Long contestId, Long userId, String username) {
        RankingContext context = rankings.get(contestId);
        if (context == null) {
            return;
        }
        context.lock.writeLock().lock();
        try {
            if (context.closed || !context.initialized) {
                return;
            }
            boolean changed = context.ranking.addParticipate(userId, username);
            // 处理判题通知先于报名通知到达的情况，不查询提交表。
            Map<Long, TreeMap<Long, ContestJudgeResultMessage>> records = context.results.get(userId);
            if (records != null) {
                for (Map.Entry<Long, TreeMap<Long, ContestJudgeResultMessage>> entry : records.entrySet()) {
                    changed |= context.ranking.updateProblemState(userId, entry.getKey(),
                            RankCalculator.calculateProblem(entry.getValue().values(), context.contest.getStartTime()));
                }
            }
            if (changed) {
                context.rankingVersion++;
            }
        } finally {
            context.lock.writeLock().unlock();
        }
    }

    /** 报名业务已验证赛前取消后，移除对应行。 */
    public void removeParticipant(Long contestId, Long userId) {
        RankingContext context = rankings.get(contestId);
        if (context != null) {
            context.lock.writeLock().lock();
            try {
                if (!context.closed && context.initialized) {
                    if (context.ranking.removeParticipate(userId)) {
                        context.rankingVersion++;
                    }
                    context.results.remove(userId);
                }
            } finally {
                context.lock.writeLock().unlock();
            }
        }
    }

    /** 查询版本缓存并复制当前页；返回 null 表示已关闭，由接口读取阶段决定后续响应。 */
    public IPage<UserRankRow> getRankingPage(Long contestId, IPage<UserRankRow> page) {
        RankingContext context = context(contestId);
        if (context == null) {
            return null;
        }
        // 命中时，版本检查和当前页复制都在读锁内完成。
        context.lock.readLock().lock();
        try {
            if (context.closed) {
                return null;
            }
            if (context.cachedVersion == context.rankingVersion) {
                return copyPage(context.cachedRows, page);
            }
        } finally {
            context.lock.readLock().unlock();
        }

        // 重建锁始终先于读写锁获取，消息更新和关闭无需等待排序。
        synchronized (context.rebuildLock) {
            List<UserRankRow> rows;
            long version;
            context.lock.readLock().lock();
            try {
                if (context.closed) {
                    return null;
                }
                if (context.cachedVersion == context.rankingVersion) {
                    return copyPage(context.cachedRows, page);
                }
                rows = context.ranking.copyRows();
                version = context.rankingVersion;
            } finally {
                context.lock.readLock().unlock();
            }

            RankCalculator.sortAndRank(rows);

            // 发布时标记复制时的版本，不循环追赶排序期间到达的新结果。
            context.lock.writeLock().lock();
            try {
                if (context.closed) {
                    return null;
                }
                context.cachedRows = rows;
                context.cachedVersion = version;
            } finally {
                context.lock.writeLock().unlock();
            }
            // 已发布的列表只读，后续更新仅替换缓存引用，不修改该副本。
            return copyPage(rows, page);
        }
    }

    /** 只复制当前页，保留整榜排名；避免超大页码乘法溢出。 */
    private IPage<UserRankRow> copyPage(List<UserRankRow> rows, IPage<UserRankRow> page) {
        long offset = page.getCurrent() - 1;
        int start = offset > rows.size() / page.getSize() ? rows.size() :
                (int) Math.min(offset * page.getSize(), rows.size());
        int end = start + (int) Math.min(page.getSize(), rows.size() - start);
        page.setTotal(rows.size());
        page.setRecords(LiveRankings.copyRows(rows.subList(start, end)));
        return page;
    }

    /** 进入 SYNC 后关闭上下文并释放内存；旧引用仍可看到关闭标记。 */
    public void close(Long contestId) {
        RankingContext context = rankings.get(contestId);
        if (context == null) {
            return;
        }
        context.lock.writeLock().lock();
        try {
            context.closed = true;
            context.initialized = false;
            context.ranking = null;
            context.contest = null;
            context.results.clear();
            context.problemIds.clear();
            context.cachedRows = null;
            context.cachedVersion = -1;
            rankings.remove(contestId, context);
        } finally {
            context.lock.writeLock().unlock();
        }
    }



    /**
     * 获取参赛用户
     */
    private List<User> getParticipateUsers(Long contestId){
        List<Long> ids = participants.selectList(
                        Wrappers.<ContestParticipate>lambdaQuery()
                                .eq(ContestParticipate::getContestId, contestId)
                                .eq(ContestParticipate::getParticipateType, ParticipateType.REAL)
                )
                .stream().map(ContestParticipate::getUserId).collect(Collectors.toList());

        // 获取用户
        return ids.isEmpty() ? Collections.emptyList() : users.selectList(Wrappers.<User>lambdaQuery().in(User::getId, ids));
    }

    /**
     * 获取当前比赛为终态的提交
     */
    private List<Submission> getCompletedSubmission(Contest contest){
        return submissions.selectList(
                Wrappers.<Submission>lambdaQuery()
                        .select(
                                Submission::getId,
                                Submission::getContestId,
                                Submission::getUserId,
                                Submission::getProblemId,
                                Submission::getStatus,
                                Submission::getSubmissionTime,
                                Submission::getVersion
                        )
                        .eq(Submission::getContestId, contest.getId())
                        .eq(Submission::getSubmissionType, SubmissionType.CONTEST)
                        .ge(Submission::getSubmissionTime, contest.getStartTime())
                        .lt(Submission::getSubmissionTime, contest.getEndTime())
                        .in(Submission::getStatus, JudgingConstant.TERMINAL_STATUSES)
                        .orderByAsc(Submission::getId));
    }

}
