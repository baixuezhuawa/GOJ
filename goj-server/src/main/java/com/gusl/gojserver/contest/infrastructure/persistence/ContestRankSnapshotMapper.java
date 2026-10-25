package com.gusl.gojserver.contest.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gusl.common.pojo.entity.ContestRankSnapshotRecord;
import org.apache.ibatis.annotations.Mapper;

/** 比赛归档数据访问。 */
@Mapper
public interface ContestRankSnapshotMapper extends BaseMapper<ContestRankSnapshotRecord> {

}
