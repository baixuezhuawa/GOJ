package com.gusl.gojserver.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.constant.SystemConstant;
import com.gusl.gojserver.mapper.UserSubmissionStatusStatMapper;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.common.pojo.entity.UserSubmissionStatusStat;
import com.gusl.gojserver.pojo.vo.SubmissionStatusStatVo;
import com.gusl.gojserver.service.UserSubmissionStatusStatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RequiredArgsConstructor
@Service
public class UserSubmissionStatusStatServiceImpl extends ServiceImpl<UserSubmissionStatusStatMapper, UserSubmissionStatusStat> implements UserSubmissionStatusStatService {

    private final UserSubmissionStatusStatMapper submissionStatusStatMapper;

    private static final List<String> TERMINAL_STATUSES = List.of(
            JudgingConstant.COMPILE_ERROR,
            JudgingConstant.WRONG_ANSWER,
            JudgingConstant.ACCEPTED,
            JudgingConstant.TIME_LIMIT_EXCEEDED,
            JudgingConstant.MEMORY_LIMIT_EXCEEDED,
            JudgingConstant.RUNTIME_ERROR,
            SystemConstant.SYSTEM_ERROR
    );

    /**
     * 提交状态的统计
     */
    @Override
    public List<SubmissionStatusStatVo> getMySubmissionStatusStat(LoginUser loginUser) {

        List<UserSubmissionStatusStat> statusStats = submissionStatusStatMapper.selectList(
                Wrappers.<UserSubmissionStatusStat>lambdaQuery()
                        .eq(UserSubmissionStatusStat::getUserId, loginUser.getUserId())
                        .in(UserSubmissionStatusStat::getStatus, TERMINAL_STATUSES)
        );

        // 将数据库记录转换为状态与数量的对应关系。
        Map<String, Long> statusCountMap = new HashMap<>();
        for (UserSubmissionStatusStat statusStat : statusStats) {
            statusCountMap.put(
                    statusStat.getStatus(),
                    statusStat.getSubmissionCount()
            );
        }

        // 按照固定终态顺序返回，数据库不存在的状态补为 0。
        List<SubmissionStatusStatVo> result = new ArrayList<>();
        for (String status : TERMINAL_STATUSES) {
            result.add(
                    new SubmissionStatusStatVo(
                            status,
                            statusCountMap.getOrDefault(status, 0L)
                    )
            );
        }

        return result;
    }
}
