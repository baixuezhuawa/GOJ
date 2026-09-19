package com.gusl.gojserver.submission.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gusl.common.pojo.entity.Submission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SubmissionMapper extends BaseMapper<Submission> {


}
