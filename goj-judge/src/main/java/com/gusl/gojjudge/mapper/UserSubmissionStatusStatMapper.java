package com.gusl.gojjudge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gusl.common.pojo.entity.UserSubmissionStatusStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserSubmissionStatusStatMapper extends BaseMapper<UserSubmissionStatusStat> {


    void increase(@Param("userId") Long userId, @Param("status") String finalStatus);
}
