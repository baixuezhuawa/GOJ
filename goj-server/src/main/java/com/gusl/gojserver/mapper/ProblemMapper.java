package com.gusl.gojserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.gojserver.pojo.dto.ProblemPageListDto;
import com.gusl.gojserver.pojo.vo.AdminProblemPageListVo;
import com.gusl.gojserver.pojo.vo.AdminProblemReviewListVo;
import com.gusl.gojserver.pojo.vo.ProblemPageListVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    /**
     * 分页查询公开题目列表。
     *
     * @param page 分页对象
     * @param dto 查询条件
     * @param userId 当前用户 id，游客传 null
     * @return 题目列表
     */
    IPage<ProblemPageListVo> selectProblemPage(
            @Param("page") Page<ProblemPageListVo> page,
            @Param("dto") ProblemPageListDto dto,
            @Param("userId") Long userId,
            @Param("publishStatus") Integer publishStatus
    );

    /**
     * 分页查询管理员题目列表，不限制题目发布状态。
     *
     * @param page 分页对象
     * @param dto 查询条件
     * @param userId 当前管理员 id
     * @return 题目列表
     */
    IPage<AdminProblemPageListVo> selectProblemPageByAdmin(
            @Param("page") Page<AdminProblemPageListVo> page,
            @Param("dto") ProblemPageListDto dto,
            @Param("userId") Long userId
    );
}
