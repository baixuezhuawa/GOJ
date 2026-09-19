package com.gusl.gojjudge.judging.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gusl.common.pojo.entity.ContestSubmission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContestSubmissionMapper extends BaseMapper<ContestSubmission> {

}
