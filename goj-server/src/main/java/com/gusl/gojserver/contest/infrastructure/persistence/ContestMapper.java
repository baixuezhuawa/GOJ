package com.gusl.gojserver.contest.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gusl.common.pojo.entity.Contest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContestMapper extends BaseMapper<Contest> {

}
