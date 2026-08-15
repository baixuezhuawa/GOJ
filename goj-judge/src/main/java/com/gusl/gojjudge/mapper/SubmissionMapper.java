package com.gusl.gojjudge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gusl.common.pojo.entity.Submission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提交记录持久化访问接口。
 */
@Mapper
public interface SubmissionMapper extends BaseMapper<Submission> {

}
