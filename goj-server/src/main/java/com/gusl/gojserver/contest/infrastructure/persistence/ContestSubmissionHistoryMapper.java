package com.gusl.gojserver.contest.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gusl.common.pojo.entity.ContestSubmissionHistory;
import org.apache.ibatis.annotations.Mapper;

/** 比赛归档数据访问。 */
@Mapper
public interface ContestSubmissionHistoryMapper extends BaseMapper<ContestSubmissionHistory> {

}
