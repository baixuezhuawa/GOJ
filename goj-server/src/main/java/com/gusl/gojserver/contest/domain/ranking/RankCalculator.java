package com.gusl.gojserver.contest.domain.ranking;

import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.pojo.entity.ContestJudgeResultMessage;
import com.gusl.gojserver.contest.application.model.ProblemState;
import com.gusl.gojserver.contest.application.model.UserRankRow;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** 排行榜计算器，共用 ACM 计罚、单题回放和并列排名规则。 */
public class RankCalculator {

    /** 按提交 ID 排好序的单题记录重算题格，供乱序结果和重测使用。 */
    public static ProblemState calculateProblem(Iterable<ContestJudgeResultMessage> results, LocalDateTime start) {
        ProblemState state = new ProblemState(0, false, -1);
        for (ContestJudgeResultMessage result : results) {
            state = applyOrderedResult(
                    state,
                    result.getStatus(),
                    (int) Duration.between(start, result.getSubmissionTime()).toMinutes()
            );
        }
        return state;
    }

    /** 应用顺序到达的一份最终结果，返回新题格，不修改原题格。 */
    public static ProblemState applyOrderedResult(ProblemState previous, String status, int minute) {
        ProblemState state = new ProblemState(previous.getAttempt(), previous.isAccepted(), previous.getAcceptedMinute());
        if (!state.isAccepted()) {
            if (JudgingConstant.ACCEPTED.equals(status)) {
                state.setAccepted(true);
                state.setAcceptedMinute(minute);
            } else if (isPenaltyStatus(status)) {
                state.setAttempt(state.getAttempt() + 1);
            }
        }
        return state;
    }

    /** 仅通过的题目贡献罚时。 */
    public static int penalty(ProblemState state) {
        return state.isAccepted() ? state.getAcceptedMinute() + state.getAttempt() * 20 : 0;
    }

    /** 判断应计罚的失败终态，系统错误不计罚。 */
    public static boolean isPenaltyStatus(String status) {
        return JudgingConstant.WRONG_ANSWER.equals(status)
                || JudgingConstant.COMPILE_ERROR.equals(status)
                || JudgingConstant.TIME_LIMIT_EXCEEDED.equals(status)
                || JudgingConstant.MEMORY_LIMIT_EXCEEDED.equals(status)
                || JudgingConstant.RUNTIME_ERROR.equals(status);
    }

    /** 通过数降序、罚时升序；成绩相同采用 1、1、3 排名。 */
    public static void sortAndRank(List<UserRankRow> rows) {
        rows.sort(
                Comparator.comparingInt(UserRankRow::getAcceptedCount)
                .reversed()
                .thenComparingInt(UserRankRow::getPenalty)
                .thenComparing(UserRankRow::getUserId)
        );
        int rank = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (
                    i == 0
                    || rows.get(i).getAcceptedCount() != rows.get(i - 1).getAcceptedCount()
                    || rows.get(i).getPenalty() != rows.get(i - 1).getPenalty()
            ) {
                rank = i + 1;
            }
            rows.get(i).setRank(rank);
        }
    }
}
