package com.gusl.gojserver.contest.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.common.common.BaseException;
import com.gusl.common.constant.ContestStatus;
import com.gusl.common.constant.ParticipateType;
import com.gusl.common.pojo.entity.Contest;
import com.gusl.common.pojo.entity.ContestParticipate;
import com.gusl.gojserver.contest.infrastructure.persistence.ContestMapper;
import com.gusl.gojserver.contest.infrastructure.persistence.ContestParticipateMapper;
import com.gusl.gojserver.account.infrastructure.security.LoginUser;
import lombok.RequiredArgsConstructor;
import com.gusl.gojserver.contest.application.ranking.RankingManager;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ContestParticipateServiceImpl extends ServiceImpl<ContestParticipateMapper, ContestParticipate> implements ContestParticipateService {

    private final ContestParticipateMapper participateMapper;

    private final ContestMapper contestMapper;

    private final RankingManager rankingManager;

    /**
     * 报名比赛
     */
    @Override
    public void registerContest(Long contestId, LoginUser loginUser) {
        // 判断比赛是否已经结束报名
        Contest contest = contestMapper.selectOne(
                Wrappers.<Contest> lambdaQuery()
                        .eq(Contest::getId, contestId)
                        .in(Contest::getStatus, ContestStatus.SCHEDULED, ContestStatus.RUNNING)
        );
        if (contest == null) {
            throw new BaseException("该比赛不存在");
        }

        LocalDateTime now = LocalDateTime.now();

        // 判断是否在报名时间
        if(
                contest.getRegisterStartTime().isAfter(now) ||
                !now.isBefore(contest.getEndTime())
        ){
            throw new BaseException(
                    "不在合法报名时间" +
                    contest.getRegisterStartTime() + " ~ " +
                    contest.getEndTime()
            );
        }

        // 报名, 但是有可能重复报名
        ContestParticipate participate = new ContestParticipate();
        participate.setContestId(contestId);
        participate.setUserId(loginUser.getUserId());
        participate.setRegisterTime(now);
        participate.setParticipateType(ParticipateType.REAL);

        try {
            // 有联合索引(user_id, 参数类型, contest_id), 防止重复报名.
            participateMapper.insert(participate);
        } catch (DuplicateKeyException e) {
           throw new BaseException("你已重复报名该比赛");
        }
        rankingManager.addParticipant(contestId, loginUser.getUserId(), loginUser.getUsername());
    }

    /**
     * 取消报名
     */
    @Override
    public void logoutContest(Long contestId, LoginUser loginUser) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) {
            throw new BaseException("该比赛不存在");
        }

        LocalDateTime now = LocalDateTime.now();

        // 比赛开始后不能取消报名，不依赖定时任务是否已更新比赛状态。
        if (!now.isBefore(contest.getStartTime())) {
            throw new BaseException("比赛已开始，不能取消报名");
        }

        // 判断是否在报名时间
        if(
                contest.getRegisterStartTime().isAfter(now) ||
                        contest.getRegisterEndTime().isBefore(now)
        ){
            throw new BaseException(
                    "不在合法报名时间, 不能取消报名" +
                            contest.getRegisterStartTime() + " ~ " +
                            contest.getRegisterEndTime()
            );
        }

        // 取消报名
        int delete = participateMapper.delete(
                Wrappers.<ContestParticipate>lambdaQuery()
                        .eq(ContestParticipate::getContestId, contestId)
                        .eq(ContestParticipate::getUserId, loginUser.getUserId())
                        .eq(ContestParticipate::getParticipateType, ParticipateType.REAL)
        );

        if (delete != 1){
            throw new BaseException("未报名");
        }
        rankingManager.removeParticipant(contestId, loginUser.getUserId());
    }
}
