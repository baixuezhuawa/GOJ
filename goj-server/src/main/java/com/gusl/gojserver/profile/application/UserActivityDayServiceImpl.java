package com.gusl.gojserver.profile.application;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.gojserver.profile.infrastructure.persistence.UserActivityDayMapper;
import com.gusl.common.pojo.entity.UserActivityDay;
import org.springframework.stereotype.Service;

@Service
public class UserActivityDayServiceImpl extends ServiceImpl<UserActivityDayMapper, UserActivityDay> implements UserActivityDayService {

}
