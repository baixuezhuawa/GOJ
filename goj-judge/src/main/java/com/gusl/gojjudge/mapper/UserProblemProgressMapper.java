package com.gusl.gojjudge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gusl.common.pojo.entity.UserProblemProgress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface UserProblemProgressMapper extends BaseMapper<UserProblemProgress> {


    void increase(
            @Param("userId") Long userId,
            @Param("problemId") Long problemId,
            @Param("progressStatus") String progressStatus,
            @Param("submissionId") Long submissionId,
            @Param("submissionTime") LocalDateTime submissionTime,
            @Param("acceptedIncrement") Integer acceptedIncrement,
            @Param("person") String createBy
    );

    /**
     * 查询用户第一次通过指定题目时的提交 id。
     *
     * @param userId 用户 id
     * @param problemId 题目 id
     * @return 第一次通过的提交 id
     */
    Long selectFirstAcceptedSubmissionId(
            @Param("userId") Long userId,
            @Param("problemId") Long problemId
    );

}
