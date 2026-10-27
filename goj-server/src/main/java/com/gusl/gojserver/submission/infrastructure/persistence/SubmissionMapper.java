package com.gusl.gojserver.submission.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gusl.common.pojo.entity.Submission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SubmissionMapper extends BaseMapper<Submission> {

    /** 每次从主库读取结果摘要，避免复用 MyBatis 会话中的历史查询结果。 */
    @Select("SELECT id, submission_type, contest_id, user_id, version, status, score, time_ms, memory_kb, "
            + "compiler_msg, judge_msg, judge_start_time, judge_end_time FROM submission WHERE id = #{id}")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    Submission findStatus(@Param("id") Long id);

    /**
     * 读取已受理凭据；NOT_SUPPORTED 的事务同步仍可能复用 SqlSession，
     * 每次查询清除一级缓存并禁用二级缓存，避免异常后读到第一次查询的空结果。
     */
    @Select("SELECT id, submission_type, request_sha256 FROM submission "
            + "WHERE user_id = #{userId} AND request_id = #{requestId}")
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    Submission findAcceptedRequest(@Param("userId") Long userId, @Param("requestId") String requestId);


}
