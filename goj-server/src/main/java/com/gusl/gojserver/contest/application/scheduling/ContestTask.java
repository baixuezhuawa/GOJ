package com.gusl.gojserver.contest.application.scheduling;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.constant.*;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojserver.contest.application.ranking.RankingArchiveService;
import com.gusl.gojserver.contest.application.ranking.RankingManager;
import com.gusl.gojserver.contest.infrastructure.persistence.ContestMapper;
import com.gusl.gojserver.contest.infrastructure.persistence.ContestProblemMapper;
import com.gusl.gojserver.submission.infrastructure.persistence.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

/** 比赛阶段编排，依次推进 RUNNING、WAITING、SYNC、FINISH。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContestTask {
    private final ContestMapper contestMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final SubmissionMapper submissions;
    private final RankingManager rankingManager;
    private final RankingArchiveService archive;
    @Value("${goj.contest.ranking.prewarm-seconds:60}")
    private long prewarmSeconds;

    /** 赛前预热；开赛后开放题目并推进状态，单场失败不阻止其他比赛。 */
    @Scheduled(fixedDelayString = "5000")
    public void beginner() {
        LocalDateTime now = LocalDateTime.now();
        List<Contest> contests = contestMapper.selectList(
                Wrappers.<Contest>lambdaQuery()
                .eq(Contest::getStatus, ContestStatus.SCHEDULED)
                .le(Contest::getStartTime, now.plusSeconds(prewarmSeconds))
        );
        for (Contest contest : contests) {
            try {
                rankingManager.initialize(contest.getId());
                if (!contest.getStartTime().isAfter(now)) {
                    contestProblemMapper.update(
                            Wrappers.<ContestProblem>lambdaUpdate()
                            .set(ContestProblem::getReleaseStatus, ContestProblemStatus.OPENING)
                            .eq(ContestProblem::getContestId, contest.getId())
                            .eq(ContestProblem::getReleaseStatus, ContestProblemStatus.HIDDEN)
                    );
                    transition(contest, ContestStatus.RUNNING);
                }
            } catch (Exception exception) {
                log.error("比赛初始化失败，contestId={}", contest.getId(), exception);
            }
        }
    }

    /** 结束后先等待判题，再归档；未来 Hack 和系统重测阶段可以插入 SYNC 前。 */
    @Scheduled(fixedDelayString = "4990")
    public void ending() {
        List<Contest> contests = contestMapper.selectList(
                Wrappers.<Contest>lambdaQuery()
                        .in(Contest::getStatus, ContestStatus.RUNNING, ContestStatus.WAITING, ContestStatus.SYNC)
                        .le(Contest::getEndTime, LocalDateTime.now()));

        for (Contest contest : contests) {
            try {
                if (ContestStatus.RUNNING.equals(contest.getStatus())) {
                    transition(contest, ContestStatus.WAITING);
                } else if (ContestStatus.WAITING.equals(contest.getStatus())) {
                    // 判断是否还有在测评中的提交.
                    long pending = submissions.selectCount(
                            Wrappers.<Submission>lambdaQuery()
                                    .eq(Submission::getContestId, contest.getId())
                                    .eq(Submission::getSubmissionType, SubmissionType.CONTEST)
                                    .ge(Submission::getSubmissionTime, contest.getStartTime())
                                    .lt(Submission::getSubmissionTime, contest.getEndTime())
                                    .notIn(Submission::getStatus, JudgingConstant.TERMINAL_STATUSES)
                    );
                    // 如果没有则进入下一个阶段.
                    if (pending == 0 && transition(contest, ContestStatus.SYNC)) {
                        rankingManager.close(contest.getId());
                    }
                } else if (ContestStatus.SYNC.equals(contest.getStatus())) {
                    // 补偿阶段更新成功但尚未清理的情况，重复关闭不会重建榜单。
                    rankingManager.close(contest.getId());
                    // archive 为独立 Spring Bean，返回时数据库归档事务已经提交。
                    // TODO 待处理归档失败的逻辑, 和具体优化策略, 目前仅仅保留功能.
                    archive.archive(contest.getId());

                    contestProblemMapper.update(Wrappers.<ContestProblem>lambdaUpdate()
                            .set(ContestProblem::getReleaseStatus, ContestProblemStatus.PUBLISH)
                            .eq(ContestProblem::getContestId, contest.getId()));

                    transition(contest, ContestStatus.FINISH);
                }

            } catch (Exception exception) {
                log.error("比赛阶段处理失败，保留当前阶段等待重试，contestId={}, status={}",
                        contest.getId(), contest.getStatus(), exception);
            }

        }
    }

    /** 使用旧状态作为更新条件，防止覆盖已经变化的阶段。 */
    private boolean transition(Contest contest, String next) {
        return contestMapper.update(
                Wrappers.<Contest>lambdaUpdate()
                        .set(Contest::getStatus, next)
                        .eq(Contest::getId, contest.getId())
                        .eq(Contest::getStatus, contest.getStatus())
        ) == 1;
    }
}
