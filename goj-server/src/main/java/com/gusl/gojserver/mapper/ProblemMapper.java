package com.gusl.gojserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.gojserver.pojo.dto.ProblemPageListDto;
import com.gusl.gojserver.pojo.vo.AdminProblemReviewListVo;
import com.gusl.gojserver.pojo.vo.ProblemPageListVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {

    /**
     * 分页查询待审核题目。
     *
     * @param status 题目状态
     * @return 待审核题目列表
     */
    IPage<AdminProblemReviewListVo> selectPendingReviews(
            @Param("page") Page<AdminProblemReviewListVo> page,
            @Param("status") Integer status
    );

    IPage<ProblemPageListVo> selectProblemPage(@Param("page") Page<ProblemPageListVo> page,
                                               @Param("dto") ProblemPageListDto dto,
                                               @Param("userId") Long userId,
                                               @Param("publishStatus") Integer publish
    );
}
