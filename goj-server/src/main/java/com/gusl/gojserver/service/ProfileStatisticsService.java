package com.gusl.gojserver.service;


import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.vo.ProfileStatisticsVo;

public interface ProfileStatisticsService {

    ProfileStatisticsVo getProfileStatistics(LoginUser loginUser);
}
