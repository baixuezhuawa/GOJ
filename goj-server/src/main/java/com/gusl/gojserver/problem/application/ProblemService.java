package com.gusl.gojserver.problem.application;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.common.common.PageResult;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.gojserver.account.infrastructure.security.LoginUser;
import com.gusl.gojserver.problem.application.dto.ProblemSaveDto;
import com.gusl.gojserver.problem.application.dto.ProblemPageListDto;
import com.gusl.gojserver.problem.application.vo.AdminProblemPageListVo;
import com.gusl.gojserver.problem.application.vo.ProblemInfoVo;
import com.gusl.gojserver.problem.application.vo.ProblemPageListVo;
import com.gusl.gojserver.problem.application.vo.TotalProblemInfoVo;

/** 题目服务，提供公开查询和管理员题目管理。 */
public interface ProblemService extends IService<Problem> {

    /** 获取公开题目列表。 */
    PageResult<ProblemPageListVo> getProblemList(ProblemPageListDto dto, LoginUser loginUser);

    /** 获取公开题目详情。 */
    ProblemInfoVo getProblemInfoById(Long id);

    /** 创建题目，初始状态为草稿。 */
    Long createProblem(ProblemSaveDto dto, LoginUser loginUser);

    /** 编辑题目基本信息。 */
    void updateProblem(Long problemId, ProblemSaveDto dto);

    /** 管理员直接设置草稿、发布或停用状态。 */
    void setProblemStatus(Long problemId, Integer status);

    /** 获取管理员题目列表。 */
    PageResult<AdminProblemPageListVo> getProblemListByAdmin(ProblemPageListDto dto, LoginUser loginUser);

    /** 获取包含测试数据版本的完整题目信息。 */
    TotalProblemInfoVo getTotalProblemInfo(Long problemId);

    /** 设置题目难度。 */
    void setProblemDifficultByAdmin(Long problemId, Integer difficult);

    /** 添加题目标签。 */
    void addProblemTag(Long problemId, Long tagId);

    /** 删除题目标签。 */
    void deleteProblemTag(Long problemId, Long tagId);
}
