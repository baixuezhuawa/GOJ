package com.gusl.gojserver.contest.application;


import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.common.BaseException;
import com.gusl.common.common.PageQuery;
import com.gusl.common.common.PageResult;
import com.gusl.common.constant.ContestStatus;
import com.gusl.common.constant.SubmissionType;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojserver.contest.infrastructure.persistence.ContestMapper;
import com.gusl.gojserver.contest.infrastructure.persistence.ContestParticipateMapper;
import com.gusl.gojserver.contest.infrastructure.persistence.ContestProblemMapper;
import com.gusl.gojserver.submission.infrastructure.persistence.SubmissionMapper;
import com.gusl.gojserver.account.infrastructure.persistence.UserMapper;
import com.gusl.gojserver.contest.application.model.ContestRankSnapshot;
import com.gusl.gojserver.account.domain.model.User;
import com.gusl.gojserver.contest.application.model.UserRankRow;
import com.gusl.gojserver.contest.domain.ranking.RankCalculator;
import com.gusl.gojserver.contest.application.vo.ContestRankingVo;
import com.gusl.gojserver.contest.application.model.ProblemState;
import com.gusl.gojserver.shared.application.pagination.PageFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContestRankService {

    private final ContestMapper contestMapper;

    private final SubmissionMapper submissionMapper;

    private final ContestProblemMapper contestProblemMapper;

    private final ContestParticipateMapper contestParticipateMapper;

    private final UserMapper userMapper;

    private final PageFactory pageFactory;

    private final RedisTemplate<String, Object> redisTemplate;

    private final ThreadPoolTaskExecutor contestRankExecutor;


    @Value("${goj.contest.rank-cache.running-ttl-seconds}")
    private Integer runningTtl;

    @Value("${goj.contest.rank-cache.waiting-ttl-seconds}")
    private Integer waitingTtl;

    @Value("${goj.contest.rank-cache.finish-ttl-seconds}")
    private Integer finishTtl;

    private static final String RANK_SNAPSHOT_KEY_PREFIX = "contest:rank:snapshot:v2:";

    /**
     * 获取比赛排行榜
     * @param contestId 比赛id
     * @param pageQuery 分页查询
     * @return 排行榜
     */
    public ContestRankingVo getContestRank(Long contestId, PageQuery pageQuery) {
        ContestRankingVo vo = new ContestRankingVo();

        // 获取比赛
        Contest contest = contestMapper.selectOne(
                Wrappers.<Contest>lambdaQuery()
                        .notIn(Contest::getStatus, ContestStatus.DRAFT, ContestStatus.SCHEDULED)
                        .eq(Contest::getId, contestId)
        );

        if(contest == null){
            throw new BaseException("该比赛不存在");
        }

        vo.setContestId(contestId);
        vo.setContestStatus(contest.getStatus());
        vo.setContestProblems(contestProblemMapper.selectList(Wrappers.<ContestProblem>lambdaQuery()
                .eq(ContestProblem::getContestId, contestId).orderByAsc(ContestProblem::getSortOrder)));

        // 查询Redis, 获取榜快照
        ContestRankSnapshot snapshot = getContestSnapshot(contestId);

        // 如果不在缓存
        if (snapshot == null){
            try {
                snapshot = new ContestRankSnapshot(
                        contestId,
                        calContestRank(contestId, contest),
                        LocalDateTime.now()
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("排行榜计算被中断", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("排行榜数据查询失败", e);
            }
            // 保存榜单快照.
            saveContestSnapshot(contestId, snapshot, getContestTtl(contest.getStatus()));
        }

        vo.setContestRankingRows(PageResult.of(getRakingRowPage(pageQuery, snapshot.getRows())));

        return vo;
    }


    /**
     * 获取比赛排行榜快照
     * @param contestId 比赛id
     * @return 榜单快照
     */
    private ContestRankSnapshot getContestSnapshot(Long contestId){
        Object value = redisTemplate.opsForValue().get(getSnapshotKey(contestId));
        return value == null ? null : (ContestRankSnapshot)value;
    }

    /**
     * 榜单快照
     * @param contestId 比赛id
     * @param snapshot 快照
     * @param ttl 时效/s
     */
    private void saveContestSnapshot(Long contestId, ContestRankSnapshot snapshot, Integer ttl){
        redisTemplate.opsForValue().set(getSnapshotKey(contestId), snapshot, Duration.ofSeconds(ttl));
    }

    /**
     * 获取比赛榜单缓存key
     * @param contestId 比赛id
     * @return 缓存key
     */
    private String getSnapshotKey(Long contestId){
        return RANK_SNAPSHOT_KEY_PREFIX + contestId;
    }


    /**
     * 基于数据库计算比赛榜单
     * @param contestId 比赛id
     * @param contest 比赛
     * @return 比赛榜单列表
     */
    private List<UserRankRow> calContestRank(Long contestId, Contest contest) throws ExecutionException, InterruptedException {

        // 获取全部参赛人员.
        List<ContestParticipate> participates = contestParticipateMapper.selectList(
                Wrappers.<ContestParticipate>lambdaQuery()
                        .eq(ContestParticipate::getContestId, contestId)
                        .eq(ContestParticipate::getParticipateType, com.gusl.common.constant.ParticipateType.REAL)
        );

        // 如果无人报名则直接返回空列表
        if(CollectionUtil.isEmpty(participates)){
            return Collections.emptyList();
        }

        // 获取实际用户
        Future<List<User>> userFuture = contestRankExecutor.submit(() -> {
            List<Long> userIds = participates.stream()
                    .map(ContestParticipate::getUserId)
                    .collect(Collectors.toList());
            return userMapper.selectList(
                    Wrappers.<User>lambdaQuery()
                            .in(User::getId, userIds)
                            .orderByAsc(User::getId)
            );
        });

        // 获取比赛问题
        Future<List<ContestProblem>> contestProblemSubmit = contestRankExecutor.submit(() ->
                contestProblemMapper.selectList(
                        Wrappers.<ContestProblem>lambdaQuery()
                                .eq(ContestProblem::getContestId, contestId)
                                .orderByAsc(ContestProblem::getProblemId)
                )
        );

        // 获取比赛全部提交
        Future<List<Submission>> submissionSubmit = contestRankExecutor.submit(() ->
                submissionMapper.selectList(
                        Wrappers.<Submission>lambdaQuery()
                                .eq(Submission::getContestId, contestId)
                                .eq(Submission::getSubmissionType, SubmissionType.CONTEST)
                                .ge(Submission::getSubmissionTime, contest.getStartTime())
                                .lt(Submission::getSubmissionTime, contest.getEndTime())
                                .orderByAsc(Submission::getUserId)
                                .orderByAsc(Submission::getProblemId)
                                .orderByAsc(Submission::getId)
                )
        );

        List<User> users = userFuture.get();

        List<ContestProblem> contestProblems = contestProblemSubmit.get();

        List<Submission> submissions = submissionSubmit.get();

        Map<Long, Integer> id2Index = new HashMap<>();
        contestProblems.forEach(p -> id2Index.put(p.getProblemId(), p.getSortOrder()));


        // 开始计算结果
        List<CompletableFuture<UserRankRow>> futures = new ArrayList<>();

        // 对于每个用户来说
        for(User user : users){

            CompletableFuture<UserRankRow> completableFuture = CompletableFuture.supplyAsync(
                    () -> calculateUserRowVo(user, submissions, contestProblems, contest, id2Index),
                    contestRankExecutor
            );

            futures.add(completableFuture);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<UserRankRow> rows = new ArrayList<>();

        futures.forEach(e -> rows.add(e.join()));

        sortRowByRank(rows);

        return rows;
    }

    /**
     * 基于二分计算单个用户
     * @param user 用户
     * @param submissions 提交集合
     * @param contestProblems 比赛问题集合
     * @param contest 比赛实体
     * @return 单个用户比赛状态
     */
    private UserRankRow calculateUserRowVo(
            User user,
            List<Submission> submissions,
            List<ContestProblem> contestProblems,
            Contest contest,
            Map<Long, Integer> id2Index
    ){
        Long userId = user.getId();

        UserRankRow rowVo = new UserRankRow();
        rowVo.setUserId(userId);
        rowVo.setUsername(user.getUsername());

        // 通过二分定位到当前用户提交区间的起点.
        int l = 0;
        int r = submissions.size() - 1;
        int m;
        while(l <= r){
            m = l + r >> 1;
            if(submissions.get(m).getUserId() >= userId){
                r = m - 1;
            }else {
                l = m + 1;
            }
        }

        List<ProblemState> problemResultsList = new ArrayList<>(Collections.nCopies(contestProblems.size(), null));

        int p = l;
        int solve = 0;
        int penalty = 0;

        // 比赛问题已经根据问题id进行排序.
        for(ContestProblem cs : contestProblems){

            ProblemState problemResults = new ProblemState(0, false, -1);

            // 移动到指定区间
            while(
                    p < submissions.size() &&
                    submissions.get(p).getUserId().equals(userId) &&
                    submissions.get(p).getProblemId() < cs.getProblemId()
            ){
                p++;
            }

            // 处理每个问题
            while(
                    p < submissions.size() &&
                    submissions.get(p).getUserId().equals(userId) &&
                    submissions.get(p).getProblemId().equals(cs.getProblemId())
            ){
                Submission submission = submissions.get(p++);

                int minute = (int) Duration.between(contest.getStartTime(), submission.getSubmissionTime()).toMinutes();
                ProblemState updated = RankCalculator.applyOrderedResult(problemResults, submission.getStatus(), minute);
                solve += (updated.isAccepted() ? 1 : 0) - (problemResults.isAccepted() ? 1 : 0);
                penalty += RankCalculator.penalty(updated) - RankCalculator.penalty(problemResults);
                problemResults = updated;
            }

            problemResultsList.set(id2Index.get(cs.getProblemId()), problemResults);
        }

        rowVo.setStates(problemResultsList);
        rowVo.setAcceptedCount(solve);
        rowVo.setPenalty(penalty);

        return rowVo;
    }


    /**
     * 排序, 计算排名
     * @param list 用户比赛提交信息列表
     */
    private void sortRowByRank(List<UserRankRow> list){
        RankCalculator.sortAndRank(list);
    }


    /**
     * 根据比赛状态获取不同有效时间
     * @param status 比赛状态
     * @return 有效时间
     */
    private Integer getContestTtl(String status){
        if(ContestStatus.RUNNING.equals(status)){
            return runningTtl;
        }
        if(ContestStatus.WAITING.equals(status)){
            return waitingTtl;
        }
        if(ContestStatus.FINISH.equals(status)){
            return finishTtl;
        }
        return 1;
    }



    /**
     * 转化为分页对象
     * @param pageQuery 分页请求
     * @param vos 原始榜单数据
     * @return 榜单分院数据
     */
    private IPage<UserRankRow> getRakingRowPage(PageQuery pageQuery, List<UserRankRow> vos){

        IPage<UserRankRow> page = pageFactory.create(pageQuery);

        long total = vos.size();
        long offset = (page.getCurrent() - 1) * page.getSize();

        // 可能其实页就大于最大数量了
        int start = (int) Math.min(offset, total);
        int end = (int) Math.min(offset + page.getSize(), total);

        page.setRecords(vos.subList(start, end));

        page.setTotal(vos.size());

        return page;
    }


}
