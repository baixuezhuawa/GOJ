package com.gusl.gojserver.contest.application.model;

import com.gusl.common.pojo.entity.Contest;
import com.gusl.common.pojo.entity.ContestProblem;
import com.gusl.gojserver.account.domain.model.User;
import com.gusl.gojserver.contest.domain.ranking.RankCalculator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 单场排行榜结构；共享实例由 RankingManager 控制访问，不负责锁和消息幂等。 */
public class LiveRankings {

    /** 比赛题目集合 */
    private final List<ContestProblem> contestProblems;

    /** 问题 id 映射到 UserRankRow 的 states 下标，沿用创建比赛时的 sortOrder 约束。*/
    private final Map<Long, Integer> idToIndex = new HashMap<>();

    /** 记录每个用户对应比赛情况 */
    private final Map<Long, UserRankRow> rowMap = new HashMap<>();


    /** 初始化题目定位和参赛者空行。 */
    public LiveRankings(Contest contest, List<User> participants, List<ContestProblem> contestProblems) {
        this.contestProblems = contestProblems;

        contestProblems.forEach(cp -> idToIndex.put(cp.getProblemId(), cp.getSortOrder()));

        participants.forEach(user -> addParticipate(user.getId(), user.getUsername()));
    }



    /** 新报名增加空行，返回展示数据是否变化。 */
    public boolean addParticipate(Long userId, String username) {
        if (rowMap.containsKey(userId)) {
            return false;
        }

        List<ProblemState> states = new ArrayList<>();

        for (int i = 0; i < contestProblems.size(); i++) {
            states.add(new ProblemState(0, false, -1));
        }

        rowMap.put(userId, new UserRankRow(0, userId, username, 0, 0, states));
        return true;
    }

    /** 删除选手，供赛前取消报名和 VP 替换历史行使用。 */
    public boolean removeParticipate(Long userId) {
        return rowMap.remove(userId) != null;
    }


    /** 顺序回放一条历史结果，不需要提交明细或版本索引。 */
    public void applyOrderedResult(Long userId, Long problemId, String status, int minute) {
        UserRankRow row = rowMap.get(userId);
        Integer index = idToIndex.get(problemId);
        if (row != null && index != null) {
            updateProblemState(
                    userId, problemId,
                    RankCalculator.applyOrderedResult(row.getStates().get(index), status, minute)
            );
        }
    }

    /** 替换单个题格，按差额维护总成绩，返回展示数据是否变化。 */
    public boolean updateProblemState(Long userId, Long problemId, ProblemState state) {
        UserRankRow row = rowMap.get(userId);
        Integer index = idToIndex.get(problemId);
        if (row == null || index == null) {
            return false;
        }
        // 相同题格不触发展示版本变化，但 Manager 仍保留提交记录。
        ProblemState previous = row.getStates().get(index);
        if (previous.getAttempt() == state.getAttempt()
                && previous.isAccepted() == state.isAccepted()
                && previous.getAcceptedMinute() == state.getAcceptedMinute()) {
            return false;
        }
        row.getStates().set(index, state);
        // 判断之前是否通过这题
        row.setAcceptedCount(row.getAcceptedCount() + (state.isAccepted() ? 1 : 0) - (previous.isAccepted() ? 1 : 0));
        // 重新计算罚时
        row.setPenalty(row.getPenalty() + RankCalculator.penalty(state) - RankCalculator.penalty(previous));
        return true;
    }


    /** 恢复反序列化得到的独立快照行，调用者将这些行的所有权交给本实例。 */
    public void restoreRows(List<UserRankRow> rows) {
        rowMap.clear();
        rows.forEach(row -> rowMap.put(row.getUserId(), row));
    }

    /** 深拷贝成绩，查询和快照不能持有共享内存中的可变对象。 */
    public List<UserRankRow> copyRows() {
        return copyRows(rowMap.values());
    }

    /** 复制指定成绩行及题格，供完整榜单和缓存分页共用。 */
    public static List<UserRankRow> copyRows(Collection<UserRankRow> source) {
        List<UserRankRow> rows = new ArrayList<>();
        for (UserRankRow row : source) {
            List<ProblemState> states = new ArrayList<>();
            row.getStates().forEach(
                    state -> states.add(new ProblemState(state.getAttempt(), state.isAccepted(), state.getAcceptedMinute()))
            );
            rows.add(
                    new UserRankRow(
                            row.getRank(),
                            row.getUserId(),
                            row.getUsername(),
                            row.getPenalty(),
                            row.getAcceptedCount(),
                            states
                    )
            );
        }
        return rows;
    }
}
