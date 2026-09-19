package com.gusl.gojserver.profile.application;


import com.gusl.gojserver.account.infrastructure.security.LoginUser;
import com.gusl.gojserver.profile.application.vo.ProfileStatisticsVo;

public interface ProfileStatisticsService {

    ProfileStatisticsVo getProfileStatistics(LoginUser loginUser);
}
