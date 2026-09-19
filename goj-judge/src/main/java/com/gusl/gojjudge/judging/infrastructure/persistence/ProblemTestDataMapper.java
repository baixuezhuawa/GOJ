package com.gusl.gojjudge.judging.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gusl.common.pojo.entity.ProblemTestData;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目测试数据元信息持久化访问接口。
 */
@Mapper
public interface ProblemTestDataMapper extends BaseMapper<ProblemTestData> {



}
