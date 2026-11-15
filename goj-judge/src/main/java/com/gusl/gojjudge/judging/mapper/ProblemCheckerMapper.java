package com.gusl.gojjudge.judging.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gusl.common.pojo.entity.ProblemChecker;
import org.apache.ibatis.annotations.Mapper;

/** 题目校验程序持久化接口，读取题目当前使用的校验代码。 */
@Mapper
public interface ProblemCheckerMapper extends BaseMapper<ProblemChecker> {
}
