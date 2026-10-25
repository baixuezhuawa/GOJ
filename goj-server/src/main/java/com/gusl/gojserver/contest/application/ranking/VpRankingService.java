package com.gusl.gojserver.contest.application.ranking;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.common.BaseException;
import com.gusl.common.constant.ContestStatus;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojserver.contest.application.model.LiveRankings;
import com.gusl.gojserver.contest.application.model.UserRankRow;
import com.gusl.gojserver.contest.domain.ranking.RankCalculator;
import com.gusl.gojserver.contest.infrastructure.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * VP 排行榜回放，基于快照副本前进，并覆盖当前用户的正式成绩。
 */
@Service
@RequiredArgsConstructor
public class VpRankingService {
    private final ContestMapper contests;
    private final ContestProblemMapper problems;
    private final ContestSubmissionHistoryMapper histories;
    private final ContestRankSnapshotMapper snapshots;

    /**
     * 使用本次 VP 的开始时间和提交集合回放；调用方负责场次归属和提交筛选。
     */
    public List<UserRankRow> replay(
            Long contestId,
            Long userId,
            String username,
            LocalDateTime vpStart,
            LocalDateTime now,
            List<Submission> vpSubmissions
    ) {
        Contest contest = contests.selectById(contestId);

        if (contest == null || !ContestStatus.FINISH.equals(contest.getStatus())) {
            throw new BaseException("比赛尚未完成归档，不能查询 VP 榜单");
        }

        long duration = Duration.between(contest.getStartTime(), contest.getEndTime()).toMillis();

        long elapsed = Math.clamp(duration, 0, Duration.between(vpStart, now).toMillis());

        // 时间确定截止位置，离散序号确定快照与后续回放范围。
        ContestSubmissionHistory last = histories.selectOne(
                Wrappers.<ContestSubmissionHistory>lambdaQuery()
                        .eq(ContestSubmissionHistory::getContestId, contestId)
                        .le(ContestSubmissionHistory::getElapsedMillis, elapsed)
                        .orderByDesc(ContestSubmissionHistory::getSequenceNo).last("LIMIT 1")
        );

        long cutoff = last == null ? 0 : last.getSequenceNo();

        ContestRankSnapshotRecord snapshot = snapshots.selectOne(
                Wrappers.<ContestRankSnapshotRecord>lambdaQuery()
                        .eq(ContestRankSnapshotRecord::getContestId, contestId)
                        .le(ContestRankSnapshotRecord::getLastSequence, cutoff)
                        .orderByDesc(ContestRankSnapshotRecord::getLastSequence)
                        .last("LIMIT 1")
        );

        if (snapshot == null) {
            throw new BaseException("该比赛尚无排行榜归档");
        }

        // 比赛题目
        List<ContestProblem> contestProblems = problems.selectList(
                Wrappers.<ContestProblem>lambdaQuery()
                        .eq(ContestProblem::getContestId, contestId)
                        .orderByAsc(ContestProblem::getSortOrder)
        );

        LiveRankings ranking = new LiveRankings(contest, Collections.emptyList(), contestProblems);

        // 反序列化得到独立副本，不影响其他用户的 VP 或正式榜单。
        ranking.restoreRows(JSONUtil.toList(snapshot.getRowsJson(), UserRankRow.class));

        ranking.removeParticipate(userId);

        ranking.addParticipate(userId, username);

        // 获取相对比赛时间, 在我之前, 快照之后的提交.
        List<ContestSubmissionHistory> remaining = histories.selectList(
                Wrappers.<ContestSubmissionHistory>lambdaQuery()
                        .eq(ContestSubmissionHistory::getContestId, contestId)
                        .gt(ContestSubmissionHistory::getSequenceNo, snapshot.getLastSequence())
                        .le(ContestSubmissionHistory::getSequenceNo, cutoff)
                        .orderByAsc(ContestSubmissionHistory::getSequenceNo)
        );

        // 更新快照.
        for (ContestSubmissionHistory history : remaining) {
            if (!userId.equals(history.getUserId())) {
                ranking.applyOrderedResult(
                        history.getUserId(),
                        history.getProblemId(),
                        history.getStatus(),
                        (int) (history.getElapsedMillis() / 60000)
                );
            }
        }

        // 当前用户仅使用本次 VP 的提交；相同时间仍由提交 ID 决定顺序。
        List<Submission> ordered = new ArrayList<>(vpSubmissions);

        ordered.sort(Comparator.comparing(Submission::getId));

        for (Submission submission : ordered) {
            long submitted = Duration.between(vpStart, submission.getSubmissionTime()).toMillis();
            if (userId.equals(submission.getUserId()) && submitted >= 0 && submitted <= elapsed && submitted < duration) {
                ranking.applyOrderedResult(
                        userId,
                        submission.getProblemId(),
                        submission.getStatus(),
                        (int) (submitted / 60000)
                );
            }
        }

        List<UserRankRow> rows = ranking.copyRows();

        RankCalculator.sortAndRank(rows);

        return rows;
    }
}
