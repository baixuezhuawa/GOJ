package com.gusl.gojserver.problem.application;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.common.common.BaseException;
import com.gusl.common.common.PageResult;
import com.gusl.common.constant.ProblemProgressStatus;
import com.gusl.common.constant.ProblemStatus;
import com.gusl.common.pojo.entity.ProblemTestData;
import com.gusl.common.utils.StringUtils;
import com.gusl.gojserver.problem.infrastructure.persistence.ProblemMapper;
import com.gusl.gojserver.problem.infrastructure.persistence.ProblemTestDataMapper;
import com.gusl.gojserver.problem.infrastructure.persistence.TagMapper;
import com.gusl.gojserver.account.infrastructure.persistence.UserMapper;
import com.gusl.gojserver.problem.application.dto.ProblemSaveDto;
import com.gusl.gojserver.problem.application.dto.ProblemPageListDto;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.gojserver.account.infrastructure.security.LoginUser;
import com.gusl.gojserver.account.domain.model.User;
import com.gusl.gojserver.problem.application.vo.AdminProblemPageListVo;
import com.gusl.gojserver.problem.application.vo.ProblemInfoVo;
import com.gusl.gojserver.problem.application.vo.ProblemPageListVo;
import com.gusl.gojserver.problem.application.vo.ProblemTagRow;
import com.gusl.gojserver.problem.application.vo.ProblemTagsVo;
import com.gusl.gojserver.problem.application.vo.ProblemTestDataListVo;
import com.gusl.gojserver.problem.application.vo.TotalProblemInfoVo;
import com.gusl.gojserver.shared.application.pagination.PageFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 题目服务实现，负责题目查询、上传和状态管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemServiceImpl extends ServiceImpl<ProblemMapper, Problem> implements ProblemService {

    private final ProblemMapper problemMapper;

    private final TagMapper tagMapper;

    private final ProblemTestDataService problemTestDataService;

    private final PageFactory pageFactory;
    private final UserMapper userMapper;
    private final ProblemTestDataMapper problemTestDataMapper;


    /**
     * 分页条件查询题目列表
     */
    @Override
    public PageResult<ProblemPageListVo> getProblemList(ProblemPageListDto dto, LoginUser loginUser) {

        // 创建分页参数，题目 id 排序由查询 SQL 统一指定。
        Page<ProblemPageListVo> page = pageFactory.create(dto);

        Long userId = loginUser == null ? null : loginUser.getUserId();

        validateSolveStatus(dto.getSolveStatus(), userId);

        IPage<ProblemPageListVo> problemList = problemMapper.selectProblemPage(
                page,
                dto,
                userId,
                ProblemStatus.PUBLISH
        );

        fillTags(problemList.getRecords());

        return PageResult.of(problemList);
    }

    /**
     * 根据 题目id 获取题目详细信息
     */
    @Override
    public ProblemInfoVo getProblemInfoById(Long id) {
        ProblemInfoVo info = new ProblemInfoVo();
        Problem problem = getOne(
                Wrappers.<Problem>lambdaQuery()
                        .eq(Problem::getId, id)
                        .eq(Problem::getStatus, ProblemStatus.PUBLISH)
        );
        if (problem == null) {
            throw new BaseException("题目不存在或尚未发布");
        }
        // 复制公开题目信息
        BeanUtil.copyProperties(problem, info);
        info.setAuthorName(userMapper.selectById(problem.getAuthorId()).getUsername());
        info.setTags(tagMapper.getTagByProblemId(id));
        return info;
    }

    /** 管理员创建题目，不要求同时上传测试数据。 */
    @Override
    public Long createProblem(ProblemSaveDto dto, LoginUser loginUser) {
        validateProblem(dto);
        Problem problem = BeanUtil.copyProperties(dto, Problem.class);
        problem.setAuthorId(loginUser.getUserId());
        problem.setStatus(ProblemStatus.DRAFT);
        problemMapper.insert(problem);
        return problem.getId();
    }

    /** 管理员编辑题目信息，不改变题目状态和测试数据。 */
    @Override
    public void updateProblem(Long problemId, ProblemSaveDto dto) {
        validateProblem(dto);
        Problem problem = BeanUtil.copyProperties(dto, Problem.class);
        problem.setId(problemId);
        if (problemMapper.updateById(problem) != 1) {
            throw new BaseException("题目不存在");
        }
    }

    /** 管理员直接设置状态；公开题目前检查当前启用数据。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setProblemStatus(Long problemId, Integer status) {
        if (!ProblemStatus.DRAFT.equals(status)
                && !ProblemStatus.PUBLISH.equals(status)
                && !ProblemStatus.DISABLE.equals(status)) {
            throw new BaseException("题目状态不合法");
        }
        // 与测试数据写操作使用同一个题目行锁，保证发布检查和状态写入一致。
        Problem problem = problemMapper.selectOne(
                Wrappers.<Problem>lambdaQuery().eq(Problem::getId, problemId).last("FOR UPDATE")
        );
        if (problem == null) {
            throw new BaseException("题目不存在");
        }
        if (ProblemStatus.PUBLISH.equals(status)) {
            problemTestDataService.requireActiveTestData(problemId);
        }
        problemMapper.update(Wrappers.<Problem>lambdaUpdate()
                .set(Problem::getStatus, status).eq(Problem::getId, problemId));
    }

    /** 创建和编辑使用相同的题面与资源限制校验。 */
    private void validateProblem(ProblemSaveDto dto) {
        if (dto == null || StringUtils.isEmpty(dto.getProblemName(), dto.getDescription(),
                dto.getInputDescription(), dto.getOutPutDescription())) {
            throw new BaseException("题目基本信息不能为空");
        }
        if (dto.getTimeLimit() == null || dto.getTimeLimit() <= 0
                || dto.getMemoryLimit() == null || dto.getMemoryLimit() <= 0) {
            throw new BaseException("时间限制和内存限制必须大于 0");
        }
    }

    /** 获取管理员问题列表。 */
    @Override
    public PageResult<AdminProblemPageListVo> getProblemListByAdmin(ProblemPageListDto dto, LoginUser loginUser) {
        Page<AdminProblemPageListVo> page = pageFactory.create(dto);

        Long userId = loginUser == null ? null : loginUser.getUserId();

        validateSolveStatus(dto.getSolveStatus(), userId);

        IPage<AdminProblemPageListVo> problemList = problemMapper.selectProblemPageByAdmin(
                page,
                dto,
                userId
        );

        fillTags(problemList.getRecords());

        return PageResult.of(problemList);
    }


    /** 获取问题的全部信息 */
    @Override
    public TotalProblemInfoVo getTotalProblemInfo(Long problemId) {
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null){
            throw new BaseException("该问题不存在");
        }

        User author = userMapper.selectById(problem.getAuthorId());

        if (author == null){
            throw new BaseException("用户不存在/已注销");
        }

        List<ProblemTagsVo> tags = tagMapper.getTagsByProblemId(problemId);

        List<ProblemTestData> dataList = problemTestDataMapper.selectList(
                Wrappers.<ProblemTestData>lambdaQuery()
                        .eq(ProblemTestData::getProblemId, problem.getId())
        );

        List<ProblemTestDataListVo> dataListVos = new ArrayList<>(dataList.size());
        dataList.forEach(data -> {
            dataListVos.add(BeanUtil.copyProperties(data, ProblemTestDataListVo.class));
        });

        // 问题全部信息
        TotalProblemInfoVo vo = BeanUtil.copyProperties(problem, TotalProblemInfoVo.class);

        // 作者名称
        vo.setAuthorName(author.getUsername());

        // 标签集合
        vo.setTags(tags);

        // 测试数据集合
        vo.setTestDataInfoList(dataListVos);

        return vo;
    }

    /** 设置问题难度 */
    @Override
    public void setProblemDifficultByAdmin(Long problemId, Integer difficult) {
        int update = problemMapper.update(
                Wrappers.<Problem>lambdaUpdate()
                        .set(Problem::getDifficulty, difficult)
                        .eq(Problem::getId, problemId)
        );
        if (update == 1){
            log.info("{} 难度修改成功", problemId);
        }
    }

    /**
     * 设置问题标签
     */
    @Override
    public void addProblemTag(Long problemId, Long tagId) {

        Problem problem = problemMapper.selectById(problemId);

        if (problem == null){
            throw new BaseException("该问题不存在");
        }

        tagMapper.addTagToProblem(problemId, tagId);
    }

    /** 删除问题标签 */
    @Override
    public void deleteProblemTag(Long problemId, Long tagId) {

        tagMapper.deleteProblemTag(problemId, tagId);
    }


    /**
     * 批量补充当前页题目的标签，避免逐题查询造成 N+1 次数据库访问。
     *
     * @param records 当前页题目
     */
    private void fillTags(List<? extends ProblemPageListVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        // 获取问题id集合
        List<Long> problemIds = new ArrayList<>(records.size());
        for (ProblemPageListVo record : records) {
            problemIds.add(record.getProblemId());
            record.setTags(new ArrayList<>());
        }

        // 获取问题的所有标签
        List<ProblemTagRow> tagRows = tagMapper.getTagsByProblemIds(problemIds);
        if (tagRows == null || tagRows.isEmpty()) {
            return;
        }

        // 映射每个标签到问题id中
        Map<Long, List<String>> tagsByProblemId = new HashMap<>();
        for (ProblemTagRow tagRow : tagRows) {
            tagsByProblemId
                    .computeIfAbsent(tagRow.getProblemId(), key -> new ArrayList<>())
                    .add(tagRow.getTagName());
        }

        // 给每个结果填充标签
        for (ProblemPageListVo record : records) {
            List<String> tags = tagsByProblemId.get(record.getProblemId());
            if (tags != null) {
                record.setTags(tags);
            }
        }
    }

    /**
     * 校验做题状态筛选条件。
     *
     * @param solveStatus 做题状态
     * @param userId 当前用户 id
     */
    private void validateSolveStatus(String solveStatus, Long userId) {
        if (StringUtils.isEmpty(solveStatus)) {
            return;
        }
        if (userId == null) {
            throw new BaseException("登录后才可以按照做题状态筛选");
        }
        if (!ProblemProgressStatus.UNATTEMPTED.equals(solveStatus)
                && !ProblemProgressStatus.ATTEMPTED.equals(solveStatus)
                && !ProblemProgressStatus.SOLVED.equals(solveStatus)) {
            throw new BaseException("做题状态参数错误");
        }
    }
}
