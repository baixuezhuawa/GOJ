package com.gusl.gojserver.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.common.pojo.entity.UserActivityDay;
import com.gusl.common.pojo.entity.UserProblemProgress;
import com.gusl.gojserver.mapper.SubmissionMapper;
import com.gusl.gojserver.mapper.UserActivityDayMapper;
import com.gusl.gojserver.mapper.UserProblemProgressMapper;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.vo.ProfileStatisticsVo;
import com.gusl.gojserver.service.ProfileStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileStatisticsServiceImpl implements ProfileStatisticsService {

    private final UserProblemProgressMapper problemProgressMapper;

    private final SubmissionMapper submissionMapper;

    private final UserActivityDayMapper activityDayMapper;

    @Override
    public ProfileStatisticsVo getProfileStatistics(LoginUser loginUser) {

        Long userId = loginUser.getUserId();


        // 已解决问题数量
        Long solveNumber = problemProgressMapper.selectCount(
                Wrappers.<UserProblemProgress>lambdaQuery()
                        .eq(UserProblemProgress::getUserId, userId)
                        .isNotNull(UserProblemProgress::getFirstAcceptedTime)
        );


        // 尝试过但未解决问题的数量
        Long unSolveNumber = problemProgressMapper.selectCount(
                Wrappers.<UserProblemProgress>lambdaQuery()
                        .eq(UserProblemProgress::getUserId, userId)
                        .isNull(UserProblemProgress::getFirstAcceptedTime)
        );


        // 提交总数
        Long submitCount = submissionMapper.selectCount(
                Wrappers.<Submission>lambdaQuery()
                        .eq(Submission::getUserId, userId)
        );


        // 近一个月解决问题的数量
        Long lastMonthAcceptedCount = activityDayMapper.getLastMonthAcceptedCount(userId);


        // 近一年解决问题的数量
        Long lastYearAcceptedCount = activityDayMapper.getLastYearAcceptedCount(userId);


        // 当前连续做题天数
        Integer longestConsecutiveDays = 0;
        List<UserActivityDay> list = activityDayMapper.selectList(
                Wrappers.<UserActivityDay> lambdaQuery()
                        .eq(UserActivityDay::getUserId, userId)
                        .orderByDesc(UserActivityDay::getActivityDate)
        );

        if(CollectionUtil.isNotEmpty(list)){
            // 保证了今天不刷题的话, 仍然是保持着连续天数.
            LocalDate cur = LocalDate.now();
            if(list.getFirst().getActivityDate().equals(cur)){
                longestConsecutiveDays++;
                cur = cur.minusDays(1);
            }

            for(int i = 1; i < list.size(); i++){
                if(list.get(i).getActivityDate().equals(cur)){
                    longestConsecutiveDays++;
                    cur = cur.minusDays(1);
                }
            }

        }

        return ProfileStatisticsVo.builder()
                .solveNumber(solveNumber)
                .unSolveNumber(unSolveNumber)
                .submitCount(submitCount)
                .solveLastMonth(lastMonthAcceptedCount)
                .solveLastYear(lastYearAcceptedCount)
                .longestConsecutiveDays(longestConsecutiveDays)
                .build();
    }
}
