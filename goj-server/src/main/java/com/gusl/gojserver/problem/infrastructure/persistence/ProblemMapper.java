package com.gusl.gojserver.problem.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.gojserver.problem.application.dto.ProblemPageListDto;
import com.gusl.gojserver.problem.application.vo.AdminProblemPageListVo;
import com.gusl.gojserver.problem.application.vo.ProblemPageListVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {

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
