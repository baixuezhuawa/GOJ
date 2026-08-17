package com.gusl.gojserver.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.gojserver.mapper.UserActivityDayMapper;
import com.gusl.common.pojo.entity.UserActivityDay;
import com.gusl.gojserver.service.UserActivityDayService;
import org.springframework.stereotype.Service;

@Service
public class UserActivityDayServiceImpl extends ServiceImpl<UserActivityDayMapper, UserActivityDay> implements UserActivityDayService {

}
