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
import com.gusl.gojserver.mapper.TagMapper;
import com.gusl.gojserver.pojo.dto.ProblemDraftDto;
import com.gusl.gojserver.pojo.dto.ProblemPageListDto;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.gojserver.pojo.dto.UpdateProblemDraftDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.vo.ProblemDraftInfoVo;
import com.gusl.gojserver.pojo.vo.ProblemInfoVo;
import com.gusl.gojserver.pojo.vo.ProblemPageListVo;
import com.gusl.gojserver.service.ProblemService;
import com.gusl.gojserver.service.ProblemTestDataService;
import com.gusl.gojserver.service.support.PageFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;


@Service
@RequiredArgsConstructor
public class ProblemServiceImpl extends ServiceImpl<ProblemMapper, Problem> implements ProblemService {

    private final ProblemMapper problemMapper;

    private final TagMapper tagMapper;

    private final ProblemTestDataService problemTestDataService;

    private final SysProperties sysProperties;

    private final PageFactory pageFactory;


    /**
     * 分页条件查询题目列表
     */
    @Override
    public PageResult<ProblemPageListVo> getProblemList(ProblemPageListDto dto, LoginUser loginUser) {

        // 分页信息, 根据问题 id 进行排序
        Page<ProblemPageListVo> page = pageFactory.create(dto);

        Long userId = loginUser == null ? null : loginUser.getUserId();

        // 游客没有个人做题状态，不能按照个人状态筛选。
        if (userId == null && StringUtils.isNotEmpty(dto.getSolveStatus())) {
            throw new BaseException("登录后才可以按照做题状态筛选");
        }

        String solveStatus = dto.getSolveStatus();

        if (StringUtils.isNotEmpty(solveStatus)
                && !ProblemProgressStatus.UNATTEMPTED.equals(solveStatus)
                && !ProblemProgressStatus.ATTEMPTED.equals(solveStatus)
                && !ProblemProgressStatus.SOLVED.equals(solveStatus)) {
            throw new BaseException("做题状态参数错误");
        }

        IPage<ProblemPageListVo> problemListVo = problemMapper.selectProblemPage(
                page,
                dto,
                userId,
                ProblemStatus.PUBLISH
        );

        for (ProblemPageListVo vo : problemListVo.getRecords()) {
            vo.setTags(tagMapper.getTagByProblemId(vo.getProblemId()));
        }

        return PageResult.of(problemListVo);
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
        info.setTags(tagMapper.getTagByProblemId(id));
        return info;
    }

    /**
     * 用户上传题目, 上传数据未成功, 题目默认会保存为草稿状态
     */
    @Override
    public void uploadProblemByUser(ProblemDraftDto draft, MultipartFile data, LoginUser loginUser) throws IOException {
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
    }

    /**
     * 获取已上传题目列表
     */
    @Override
    public PageResult<ProblemDraftInfoVo> getUploadProblemList(PageQuery pageQuery, LoginUser loginUser) {

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

        IPage<ProblemDraftInfoVo> res = list.convert(p -> BeanUtil.toBean(p, ProblemDraftInfoVo.class));

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
}
