package com.gusl.gojserver.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.common.common.BaseException;
import com.gusl.common.common.PageQuery;
import com.gusl.common.common.PageResult;
import com.gusl.common.constant.ProblemProgressStatus;
import com.gusl.common.constant.ProblemStatus;
import com.gusl.common.pojo.entity.ProblemTestData;
import com.gusl.common.utils.StringUtils;
import com.gusl.gojserver.config.properties.SysProperties;
import com.gusl.gojserver.mapper.ProblemMapper;
import com.gusl.gojserver.mapper.ProblemTestDataMapper;
import com.gusl.gojserver.mapper.TagMapper;
import com.gusl.gojserver.mapper.UserMapper;
import com.gusl.gojserver.pojo.dto.ProblemDraftDto;
import com.gusl.gojserver.pojo.dto.ProblemPageListDto;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.gojserver.pojo.dto.UpdateProblemDraftDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.entity.User;
import com.gusl.gojserver.pojo.vo.*;
import com.gusl.gojserver.service.ProblemService;
import com.gusl.gojserver.service.ProblemTestDataService;
import com.gusl.gojserver.service.support.PageFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
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

    private final SysProperties sysProperties;

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
        // 进行属性卡拷贝
        BeanUtil.copyProperties(problem, info);
        info.setAuthorName(userMapper.selectById(problem.getAuthorId()).getUsername());
        info.setTags(tagMapper.getTagByProblemId(id));
        return info;
    }

    /**
     * 用户上传题目, 上传数据未成功, 题目默认会保存为草稿状态
     */
    @Override
    public Long uploadProblemByUser(
            ProblemDraftDto draft,
            MultipartFile data,
            LoginUser loginUser
    ) throws IOException {
        if (StringUtils.isEmpty(
                draft.getProblemName(),
                draft.getDescription(),
                draft.getInputDescription(),
                draft.getOutPutDescription(),
                draft.getInputExample(),
                draft.getOutPutExample())
                ||
                data.isEmpty()
        ) {
            throw new BaseException("题目基本信息不能为空");
        }

        // 插入问题草稿
        if (draft.getTimeLimit() == null || draft.getTimeLimit() <= 0
                || draft.getMemoryLimit() == null || draft.getMemoryLimit() <= 0) {
            throw new BaseException("时间限制和内存限制必须大于 0");
        }

        Problem problem = BeanUtil.copyProperties(draft, Problem.class);
        problem.setAuthorId(loginUser.getUserId());
        problem.setStatus(ProblemStatus.DRAFT);
        problemMapper.insert(problem);

        problemTestDataService.uploadTestData(problem.getId(), data, loginUser);

        // 使用状态条件保证上传完成后只能从草稿进入待审核状态。
        int affectedRows = problemMapper.update(
                Wrappers.<Problem>lambdaUpdate()
                        .set(Problem::getStatus, ProblemStatus.PENDING)
                        .eq(Problem::getId, problem.getId())
                        .eq(Problem::getStatus, ProblemStatus.DRAFT)
        );
        if (affectedRows != 1) {
            throw new BaseException("题目状态更新失败");
        }
        return problem.getId();
    }

    /**
     * 获取已上传题目列表
     */
    @Override
    public PageResult<ProblemDraftListVo> getUploadProblemList(PageQuery pageQuery, LoginUser loginUser) {

        // 根据 id 进行排序
        Page<Problem> page = pageFactory.create(pageQuery, OrderItem.asc("id"));

        // 获取所有作者是我的题目
        Page<Problem> list = problemMapper.selectPage(
                page,
                Wrappers.<Problem>lambdaQuery()
                        .eq(Problem::getAuthorId, loginUser.getUserId())
        );

        if (list.getRecords() == null || list.getRecords().isEmpty()) {
            return PageResult.empty();
        }

        IPage<ProblemDraftListVo> res = list.convert(p ->
                BeanUtil.toBean(p, ProblemDraftListVo.class)
        );

        return PageResult.of(res);
    }

    /**
     * 更新题目信息
     */
    @Override
    public void updateMyProblemDraft(UpdateProblemDraftDto problemDraftDto, LoginUser loginUser) {
        // 封装修改后的题目信息，修改成功后进入草稿状态
        Problem problem = BeanUtil.copyProperties(problemDraftDto, Problem.class);
        problem.setStatus(ProblemStatus.DRAFT);

        // 在同一条 SQL 中校验作者和原状态，避免查询后题目状态发生变化
        int affectedRows = problemMapper.update(
                problem,
                Wrappers.<Problem>lambdaUpdate()
                        .eq(Problem::getId, problemDraftDto.getProblemId())
                        .eq(Problem::getAuthorId, loginUser.getUserId())
                        .in(Problem::getStatus, ProblemStatus.WITHDRAW, ProblemStatus.DRAFT)
        );
        if (affectedRows != 1) {
            throw new BaseException("题目不存在、状态已变化或无权修改");
        }
    }

    /**
     * 重新提交审核
     */
    @Override
    public void reUploadProblem(Long problemId, LoginUser loginUser) {
        int update = problemMapper.update(
                Wrappers.<Problem>lambdaUpdate()
                        .set(Problem::getStatus, ProblemStatus.PENDING)
                        .eq(Problem::getId, problemId)
                        .eq(Problem::getAuthorId, loginUser.getUserId())
                        // 题目从撤回状态, 你需要修改后就会变成草稿状态, 不然就重复提交了.
                        .eq(Problem::getStatus, ProblemStatus.DRAFT)
        );
        if (update == 0) {
            throw new BaseException("无需要提交审核的问题");
        }
    }

    /**
     * 删除我的草稿问题
     */
    @Override
    public void deleteMyProblemDraft(Long problemId, LoginUser loginUser) {
        int deleteRows = problemMapper.delete(
                Wrappers.<Problem>lambdaQuery()
                        .eq(Problem::getAuthorId, loginUser.getUserId())
                        .eq(Problem::getId, problemId)
                        .in(Problem::getStatus, ProblemStatus.WITHDRAW, ProblemStatus.DRAFT)
        );
        // 如果都没这问题, 都不需要删除对应测试数据了
        if (deleteRows == 0) {
            return;
        }
        // 还需要获取删除对应测试数据
        List<ProblemTestData> testDataList = problemTestDataService.list(
                Wrappers.<ProblemTestData>lambdaQuery()
                        .eq(ProblemTestData::getProblemId, problemId)
        );
        problemTestDataService.removeBatchByIds(testDataList);
        testDataList.forEach(testData -> {
            Path path = Path.of(sysProperties.getDataRoot(), testData.getStoragePath());
            FileUtil.del(path.getParent());
        });
    }

    /**
     * 管理员设置问题状态
     */
    @Override
    public void setProblemStatus(Long problemId, Integer status) {
        List<Integer> sourceStatuses;
        if (ProblemStatus.DISABLE.equals(status)) {
            sourceStatuses = List.of(ProblemStatus.PREPARE, ProblemStatus.PUBLISH);
        } else if (ProblemStatus.PREPARE.equals(status)) {
            sourceStatuses = List.of(ProblemStatus.DISABLE);
        } else {
            throw new BaseException("管理员只能将题目停用或恢复到准备状态");
        }

        int update = problemMapper.update(
                Wrappers.<Problem>lambdaUpdate()
                        .set(Problem::getStatus, status)
                        .eq(Problem::getId, problemId)
                        .in(Problem::getStatus, sourceStatuses)
        );
        if (update != 1){
            throw new BaseException("题目不存在或当前状态不允许修改");
        }
    }

    /**
     * 获取我上传的题目详情.
     */
    @Override
    public UploadProblemDetailVo getUploadProblemDetail(Long problemId, LoginUser loginUser) {

        Problem problem = problemMapper.selectOne(
                Wrappers.<Problem> lambdaQuery()
                        .eq(Problem::getId, problemId)
                        .eq(Problem::getAuthorId, loginUser.getUserId())
        );

        if (problem == null){
            throw new BaseException("该问题不存在");
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

        UploadProblemDetailVo vo = BeanUtil.copyProperties(problem, UploadProblemDetailVo.class);

        // 标签集合
        vo.setTags(tags);

        // 测试数据集合
        vo.setTestDataInfoList(dataListVos);

        return vo;
    }

    /**
     * 更新我的问题状态
     * @param problemId 问题id
     * @param status 数据库题目状态：1 已发布，5 准备中
     */
    @Override
    public void updateMyProblemStatus(Long problemId, Integer status, LoginUser loginUser) {

        Integer sourceStatus;
        if (ProblemStatus.PUBLISH.equals(status)) {
            sourceStatus = ProblemStatus.PREPARE;
        } else if (ProblemStatus.PREPARE.equals(status)) {
            sourceStatus = ProblemStatus.PUBLISH;
        } else {
            throw new BaseException("作者只能将题目修改为已发布或准备状态");
        }

        int update = problemMapper.update(
                Wrappers.<Problem>lambdaUpdate()
                        .set(Problem::getStatus, status)
                        .eq(Problem::getStatus, sourceStatus)
                        .eq(Problem::getAuthorId, loginUser.getUserId())
                        .eq(Problem::getId, problemId)
        );
        if (update != 1){
            throw new BaseException("题目不存在、无权修改或当前状态不允许切换");
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
