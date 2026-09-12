package com.gusl.gojserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gusl.common.pojo.entity.UserActivityDay;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserActivityDayMapper extends BaseMapper<UserActivityDay> {

    Long getLastMonthAcceptedCount(@Param("userId") Long userId);

    Long getLastYearAcceptedCount(@Param("userId") Long userId);
}
