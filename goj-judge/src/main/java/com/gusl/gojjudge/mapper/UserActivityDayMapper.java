package com.gusl.gojjudge.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gusl.common.pojo.entity.UserActivityDay;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface UserActivityDayMapper extends BaseMapper<UserActivityDay> {


    void increase(@Param("userId") Long userId,
                  @Param("activityDate") LocalDate activityDate,
                  @Param("newSolvedProblemIncrement") Integer newSolvedProblemIncrement
    );

}
