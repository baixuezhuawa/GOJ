package com.gusl.gojserver.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.constant.ContestProblemStatus;
import com.gusl.common.constant.ContestStatus;
import com.gusl.common.pojo.entity.Contest;
import com.gusl.common.pojo.entity.ContestProblem;
import com.gusl.gojserver.mapper.ContestMapper;
import com.gusl.gojserver.mapper.ContestProblemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 比赛相关定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContestTask {

    private final ContestMapper contestMapper;

    private final ContestProblemMapper contestProblemMapper;

    /**
     * 比赛开始, 更新题目状态为 OPENING
     */
    @Scheduled(fixedDelayString = "5000")
    public void beginner(){
        LocalDateTime now = LocalDateTime.now();
        List<Contest> contests = contestMapper.selectList(
                Wrappers.<Contest>lambdaQuery()
                        .eq(Contest::getStatus, ContestStatus.SCHEDULED)
                        .le(Contest::getStartTime, now)
                        .ge(Contest::getEndTime, now)
        );
        for (Contest contest : contests){
            contestMapper.update(
                    Wrappers.<Contest> lambdaUpdate()
                            .set(Contest::getStatus, ContestStatus.RUNNING)
                            .eq(Contest::getId, contest.getId())
                            .eq(Contest::getStatus, ContestStatus.SCHEDULED)
            );
            // 开放该比赛的题目
            contestProblemMapper.update(
                    Wrappers.<ContestProblem> lambdaUpdate()
                            .set(ContestProblem::getReleaseStatus, ContestProblemStatus.OPENING)
                            .eq(ContestProblem::getContestId, contest.getId())
                            .eq(ContestProblem::getReleaseStatus, ContestProblemStatus.HIDDEN)
            );
        }
    }

    /**
     * 比赛结束, 进入等待阶段, 用于等待剩余比赛测评测评结束
     */
    @Scheduled(fixedDelayString = "4990")
    public void ending(){
        LocalDateTime now = LocalDateTime.now();
        int update = contestMapper.update(
                Wrappers.<Contest>lambdaUpdate()
                        .set(Contest::getStatus, ContestStatus.WAITING)
                        .eq(Contest::getStatus, ContestStatus.RUNNING)
                        .le(Contest::getEndTime, now)
        );
        if(update >= 1){
            log.info("{}场比赛结束", update);
        }
    }

}
