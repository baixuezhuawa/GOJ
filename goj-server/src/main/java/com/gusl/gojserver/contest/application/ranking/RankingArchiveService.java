package com.gusl.gojserver.contest.application.ranking;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.common.BaseException;
import com.gusl.common.constant.*;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojserver.account.domain.model.User;
import com.gusl.gojserver.account.infrastructure.persistence.UserMapper;
import com.gusl.gojserver.contest.application.model.LiveRankings;
import com.gusl.gojserver.contest.application.model.UserRankRow;
import com.gusl.gojserver.contest.domain.ranking.RankCalculator;
import com.gusl.gojserver.contest.infrastructure.persistence.*;
import com.gusl.gojserver.submission.infrastructure.persistence.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/** 排行榜归档，先同步最终历史，再按每 100 份提交生成整榜快照。 */
@Service
@RequiredArgsConstructor
public class RankingArchiveService {

    private final ContestMapper contests;

    private final ContestProblemMapper problems;

    private final ContestParticipateMapper participants;

    private final UserMapper users;

    private final SubmissionMapper submissions;

    private final ContestSubmissionHistoryMapper histories;

    private final ContestRankSnapshotMapper snapshots;

    /** 在一个事务中替换整场归档；失败全部回滚，重试不会重复插入。 */
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long contestId) {
        // 锁定比赛行，串行执行同场归档；阶段转换仍由比赛编排负责。
        Contest contest = contests.selectOne(
                Wrappers.<Contest>lambdaQuery()
                .eq(Contest::getId, contestId).last("FOR UPDATE")
        );

        if (contest == null || !ContestStatus.SYNC.equals(contest.getStatus())) {
            throw new BaseException("当前比赛不在归档阶段");
        }

        // 加载全部提交列表.
        List<Submission> results = submissions.selectList(
                Wrappers.<Submission>lambdaQuery()
                    .select(
                            Submission::getId,
                            Submission::getUserId,
                            Submission::getProblemId,
                            Submission::getStatus,
                            Submission::getSubmissionTime
                    )
                    .eq(Submission::getContestId, contestId).eq(Submission::getSubmissionType, SubmissionType.CONTEST)
                    .ge(Submission::getSubmissionTime, contest.getStartTime())
                    .lt(Submission::getSubmissionTime, contest.getEndTime()).orderByAsc(Submission::getId));

        // 判断是否还有提交处于非终态状态.
        if (results.stream().anyMatch(result -> !JudgingConstant.TERMINAL_STATUSES.contains(result.getStatus()))) {
            throw new BaseException("正式提交尚未全部完成，不能归档");
        }

        List<ContestProblem> columns = problems.selectList(
                Wrappers.<ContestProblem>lambdaQuery()
                        .eq(ContestProblem::getContestId, contestId)
                        .orderByAsc(ContestProblem::getSortOrder)
        );

        List<Long> ids = participants.selectList(
                Wrappers.<ContestParticipate>lambdaQuery()
                        .eq(ContestParticipate::getContestId, contestId)
                        .eq(ContestParticipate::getParticipateType, ParticipateType.REAL)
                )
                .stream()
                .map(ContestParticipate::getUserId)
                .collect(Collectors.toList());

        List<User> registered = ids.isEmpty() ?
                Collections.emptyList() :
                users.selectList(Wrappers.<User>lambdaQuery().in(User::getId, ids));

        Set<Long> formalUsers = new HashSet<>(ids);

        snapshots.delete(Wrappers.<ContestRankSnapshotRecord>lambdaQuery().eq(ContestRankSnapshotRecord::getContestId, contestId));
        histories.delete(Wrappers.<ContestSubmissionHistory>lambdaQuery().eq(ContestSubmissionHistory::getContestId, contestId));

        // 按原始 ID 的顺序离散化，不使用消息到达顺序或分钟数排序。
        long sequence = 0;
        for (Submission result : results) {
            if (!formalUsers.contains(result.getUserId())) {
                continue;
            }
            ContestSubmissionHistory history = new ContestSubmissionHistory();
            history.setContestId(contestId);
            history.setSequenceNo(++sequence);
            history.setSubmissionId(result.getId());
            history.setUserId(result.getUserId());
            history.setProblemId(result.getProblemId());
            history.setElapsedMillis(Duration.between(contest.getStartTime(), result.getSubmissionTime()).toMillis());
            history.setStatus(result.getStatus());
            histories.insert(history);
        }

        // 历史同步完毕后，以历史表为输入生成快照，包含零提交和末尾不足 100 份的情况。
        LiveRankings ranking = new LiveRankings(contest, registered, columns);

        saveSnapshot(contestId, 0L, 0L, ranking);

        List<ContestSubmissionHistory> ordered = histories.selectList(
                Wrappers.<ContestSubmissionHistory>lambdaQuery()
                        .eq(ContestSubmissionHistory::getContestId, contestId)
                        .orderByAsc(ContestSubmissionHistory::getSequenceNo)
        );

        for (ContestSubmissionHistory history : ordered) {
            ranking.applyOrderedResult(
                    history.getUserId(),
                    history.getProblemId(),
                    history.getStatus(),
                    (int) (history.getElapsedMillis() / 60000)
            );
            if (history.getSequenceNo() % 100 == 0 || history.getSequenceNo() == sequence) {
                saveSnapshot(contestId, history.getSequenceNo(), history.getElapsedMillis(), ranking);
            }
        }
    }

    /** 保存独立榜单 JSON；Hutool 负责列表及题格的序列化。 */
    private void saveSnapshot(Long contestId, Long sequence, Long elapsed, LiveRankings ranking) {
        List<UserRankRow> rows = ranking.copyRows();
        RankCalculator.sortAndRank(rows);
        ContestRankSnapshotRecord snapshot = new ContestRankSnapshotRecord();
        snapshot.setContestId(contestId);
        snapshot.setLastSequence(sequence);
        snapshot.setElapsedMillis(elapsed);
        snapshot.setRowsJson(JSONUtil.toJsonStr(rows));
        snapshots.insert(snapshot);
    }

    /** 读取最终榜单；只允许读取完成归档的比赛。 */
    public List<UserRankRow> getFinalRankings(Long contestId) {
        Contest contest = contests.selectById(contestId);

        if (contest == null || !ContestStatus.FINISH.equals(contest.getStatus())) {
            throw new BaseException("比赛尚未完成归档");
        }

        ContestRankSnapshotRecord snapshot = snapshots.selectOne(Wrappers.<ContestRankSnapshotRecord>lambdaQuery()
                .eq(ContestRankSnapshotRecord::getContestId, contestId)
                .orderByDesc(ContestRankSnapshotRecord::getLastSequence).last("LIMIT 1"));

        if (snapshot == null) {
            throw new BaseException("该比赛尚无排行榜归档");
        }

        return JSONUtil.toList(snapshot.getRowsJson(), UserRankRow.class);
    }
}
