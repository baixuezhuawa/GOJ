package com.gusl.gojserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.gojserver.pojo.vo.AdminProblemReviewListVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {

    /**
     * 分页查询待审核题目。
     *
     * @param offset 分页偏移量
     * @param size 每页数量
     * @param status 题目状态
     * @return 待审核题目列表
     */
    List<AdminProblemReviewListVo> selectPendingReviews(
            @Param("offset") long offset,
            @Param("size") long size,
            @Param("status") Integer status
    );
}
