package com.gusl.gojjudge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gusl.common.pojo.entity.Problem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目持久化访问接口。
 */
@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {

}
