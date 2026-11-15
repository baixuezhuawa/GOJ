package com.gusl.gojserver.contest.application;


import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.common.BaseException;
import com.gusl.common.common.PageQuery;
import com.gusl.common.common.PageResult;
import com.gusl.common.constant.ContestStatus;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojserver.contest.infrastructure.persistence.ContestMapper;
import com.gusl.gojserver.contest.infrastructure.persistence.ContestParticipateMapper;
import com.gusl.gojserver.contest.infrastructure.persistence.ContestProblemMapper;
import com.gusl.gojserver.contest.infrastructure.persistence.ContestSubmissionMapper;
import com.gusl.gojserver.account.infrastructure.persistence.UserMapper;
import com.gusl.gojserver.contest.application.model.ContestRankSnapshot;
import com.gusl.gojserver.account.domain.model.User;
import com.gusl.gojserver.contest.application.vo.ContestRankingRowVo;
import com.gusl.gojserver.contest.application.vo.ContestRankingVo;
import com.gusl.gojserver.contest.application.vo.ProblemResults;
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

    private final ContestSubmissionMapper contestSubmissionMapper;

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

    private static final String RANK_SNAPSHOT_KEY_PREFIX = "contest:rank:snapshot:";

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
     * 计算比赛榜单
     * @param contestId 比赛id
     * @param contest 比赛
     * @return 比赛榜单列表
     */
    private List<ContestRankingRowVo> calContestRank(Long contestId, Contest contest) throws ExecutionException, InterruptedException {

        // 获取全部参赛人员.
        List<ContestParticipate> participates = contestParticipateMapper.selectList(
                Wrappers.<ContestParticipate>lambdaQuery()
                        .eq(ContestParticipate::getContestId, contestId)
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
        Future<List<ContestSubmission>> submissionSubmit = contestRankExecutor.submit(() ->
                contestSubmissionMapper.selectList(
                        Wrappers.<ContestSubmission>lambdaQuery()
                                .eq(ContestSubmission::getContestId, contestId)
                                .ge(ContestSubmission::getSubmissionTime, contest.getStartTime())
                                .lt(ContestSubmission::getSubmissionTime, contest.getEndTime())
                                .orderByAsc(ContestSubmission::getUserId)
                                .orderByAsc(ContestSubmission::getProblemId)
                                .orderByAsc(ContestSubmission::getSubmissionTime)
                                .orderByAsc(ContestSubmission::getId)
                )
        );

        List<User> users = userFuture.get();

        List<ContestProblem> contestProblems = contestProblemSubmit.get();

        List<ContestSubmission> submissions = submissionSubmit.get();


        // 开始计算结果
        List<CompletableFuture<ContestRankingRowVo>> futures = new ArrayList<>();

        // 对于每个用户来说
        for(User user : users){

            CompletableFuture<ContestRankingRowVo> completableFuture = CompletableFuture.supplyAsync(
                    () -> calculateUserRowVo(user, submissions, contestProblems, contest),
                    contestRankExecutor
            );

            futures.add(completableFuture);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<ContestRankingRowVo> rows = new ArrayList<>();

        futures.forEach(e -> rows.add(e.join()));

        sortRowByRank(rows);

        return rows;
    }

    private ContestRankingRowVo calculateUserRowVo(
            User user,
            List<ContestSubmission> submissions,
            List<ContestProblem> contestProblems,
            Contest contest
    ){
        Long userId = user.getId();

        ContestRankingRowVo rowVo = new ContestRankingRowVo();
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

        List<ProblemResults> problemResultsList = new ArrayList<>();

        int p = l;
        int solve = 0;
        int penalty = 0;

        // 比赛问题已经根据问题id进行排序.
        for(ContestProblem cs : contestProblems){

            ProblemResults problemResults = ProblemResults.builder()
                    .wrongAttempts(0)
                    .accepted(false)
                    .displayCode(cs.getDisplayCode())
                    .acceptedMinute(-1) // 没通过, 默认时间使用 -1
                    .sortOrder(cs.getSortOrder())
                    .build();

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
                ContestSubmission submission = submissions.get(p++);

                if (problemResults.isAccepted()){
                    continue;
                }

                if(JudgingConstant.ACCEPTED.equals(submission.getStatus())){
                    int minute = (int)Duration.between(contest.getStartTime(), submission.getSubmissionTime()).toMinutes();
                    problemResults.setAcceptedMinute(minute);
                    problemResults.setAccepted(true);
                    penalty += problemResults.getWrongAttempts() * 20 + minute;
                    solve++;
                }

                if(isPenaltyStatus(submission.getStatus())){
                    problemResults.setWrongAttempts(problemResults.getWrongAttempts() + 1);
                }
            }

            problemResultsList.add(problemResults);
        }

        // 再根据问题定义的顺序进行排序.
        problemResultsList.sort((a, b) -> a.getSortOrder() - b.getSortOrder());

        rowVo.setProblemResults(problemResultsList);
        rowVo.setSolveCount(solve);
        rowVo.setPenaltyMinutes(penalty);

        return rowVo;
    }



    /**
     * 排序, 计算排名
     * @param list 用户比赛提交信息列表
     */
    private void sortRowByRank(List<ContestRankingRowVo> list){
        // 排序
        list.sort((a, b) -> {
            if (!a.getSolveCount().equals(b.getSolveCount())) {
                return b.getSolveCount() - a.getSolveCount();
            }
            return a.getPenaltyMinutes() - b.getPenaltyMinutes();
        });


        for(int i = 0, rank = 0; i < list.size(); i++){
            if(
                    i == 0 || !(
                            list.get(i - 1).getPenaltyMinutes().equals(list.get(i).getPenaltyMinutes()) &&
                                    list.get(i - 1).getSolveCount().equals(list.get(i).getSolveCount())
                    )

            ){
                rank = i + 1;
            }
            list.get(i).setRank(rank);
        }
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
     * 获取比赛榜单缓存key
     * @param contestId 比赛id
     * @return 缓存key
     */
    private String getSnapshotKey(Long contestId){
        return RANK_SNAPSHOT_KEY_PREFIX + contestId;
    }


    /**
     * 转化为分页对象
     * @param pageQuery 分页请求
     * @param vos 原始榜单数据
     * @return 榜单分院数据
     */
    private IPage<ContestRankingRowVo> getRakingRowPage(PageQuery pageQuery, List<ContestRankingRowVo> vos){

        IPage<ContestRankingRowVo> page = pageFactory.create(pageQuery);

        long total = vos.size();
        long offset = (page.getCurrent() - 1) * page.getSize();

        // 可能其实页就大于最大数量了
        int start = (int) Math.min(offset, total);
        int end = (int) Math.min(offset + page.getSize(), total);

        page.setRecords(vos.subList(start, end));

        page.setTotal(vos.size());

        return page;
    }


    /**
     * 判断是否为终态
     * @param status 当前状态
     */
    private boolean isPenaltyStatus(String status) {
        return JudgingConstant.WRONG_ANSWER.equals(status)
                || JudgingConstant.COMPILE_ERROR.equals(status)
                || JudgingConstant.TIME_LIMIT_EXCEEDED.equals(status)
                || JudgingConstant.MEMORY_LIMIT_EXCEEDED.equals(status)
                || JudgingConstant.RUNTIME_ERROR.equals(status);
    }
}
