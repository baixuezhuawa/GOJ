package com.gusl.gojserver.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.common.pojo.entity.ContestParticipate;
import com.gusl.gojserver.pojo.entity.LoginUser;

public interface ContestParticipateService extends IService<ContestParticipate> {

    /**
     * 报名比赛
     */
    void registerContest(Long contestId, LoginUser loginUser);

    /**
     * 退出比赛
     */
    void logoutContest(Long contestId, LoginUser loginUser);
}
