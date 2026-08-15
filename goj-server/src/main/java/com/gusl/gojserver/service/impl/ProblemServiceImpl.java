package com.gusl.gojserver.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.common.common.BaseException;
import com.gusl.common.constant.ProblemStatus;
import com.gusl.common.pojo.entity.ProblemTestData;
import com.gusl.common.utils.StringUtils;
import com.gusl.gojserver.config.properties.JudgeProperties;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class ProblemServiceImpl extends ServiceImpl<ProblemMapper, Problem> implements ProblemService {

    private final ProblemMapper problemMapper;

    private final TagMapper tagMapper;

    private final ProblemTestDataService problemTestDataService;

    private final JudgeProperties judgeProperties;


    /** 分页条件查询题目列表 */
    @Override
    public List<ProblemPageListVo> getProblemList(ProblemPageListDto dto) {
        // 设置查询条件, 分页信息
        LambdaQueryWrapper<Problem> query = new LambdaQueryWrapper<>();
        query.eq(Problem::getStatus, ProblemStatus.PUBLISH);
        query.like(StringUtils.isNotEmpty(dto.getKeyword()), Problem::getProblemName, dto.getKeyword());
        query.between(
                ObjectUtil.isNotEmpty(dto.getDifficultyMin()) && ObjectUtil.isNotEmpty(dto.getDifficultyMax()),
                Problem::getDifficulty, dto.getDifficultyMin(), dto.getDifficultyMax()
        );
        if(ObjectUtil.isEmpty(dto.getPage())){
            dto.setPage(1);
        }
        if(ObjectUtil.isEmpty(dto.getSize())){
            dto.setSize(20);
        }
        Page<Problem> page = new Page<>(dto.getPage(), dto.getSize());

        // 获取题目列表
        List<Problem> problemList = page(page, query).getRecords();

        // 根据题目列表封装 ProblemPageListVo, 根据problemId查询对应tags
        List<ProblemPageListVo> resultList = new ArrayList<>();
        problemList.forEach(problem -> {
            ProblemPageListVo vo = new ProblemPageListVo();
            vo.setProblemId(problem.getId());
            vo.setTags(tagMapper.getTagByProblemId(problem.getId()));
            vo.setProblemName(problem.getProblemName());
            vo.setDifficulty(problem.getDifficulty());
            vo.setSolveByMe(false); // 用户是否通过设置成false先, 以后再动态
            resultList.add(vo);
        });

        return resultList;
    }

    /** 根据 题目id 获取题目详细信息 */
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

    /** 用户上传题目, 上传数据未成功, 题目默认会保存为草稿状态 */
    @Override
    public void uploadProblemByUser(ProblemDraftDto draft, MultipartFile data, LoginUser loginUser) throws IOException {
        if(StringUtils.isEmpty(
                draft.getProblemName(),
                    draft.getDescription(),
                    draft.getInputDescription(),
                    draft.getOutPutDescription(),
                    draft.getInputExample(),
                    draft.getOutPutExample())
                ||
                    data.isEmpty()
        ){
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

    /** 获取已上传题目列表 */
    @Override
    public List<ProblemDraftInfoVo> getUploadProblemList(LoginUser loginUser) {
        // 获取所有作者是我的题目
        List<Problem> list = problemMapper.selectList(
                Wrappers.<Problem>lambdaQuery()
                        .eq(Problem::getAuthorId, loginUser.getUserId())
        );
        if(list == null || list.isEmpty()){
            return List.of();
        }
        List<ProblemDraftInfoVo> res = new ArrayList<>();
        list.forEach(p -> res.add(BeanUtil.toBean(p, ProblemDraftInfoVo.class)));
        return res;
    }

    /** 更新题目信息 */
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

    /** 重新提交审核 */
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
        if(update == 0){
            throw new BaseException("无需要提交审核的问题");
        }
    }

    /** 删除我的草稿问题 */
    @Override
    public void deleteMyProblemDraft(Long problemId, LoginUser loginUser) {
        int deleteRows = problemMapper.delete(
                Wrappers.<Problem>lambdaQuery()
                        .eq(Problem::getAuthorId, loginUser.getUserId())
                        .eq(Problem::getId, problemId)
                        .in(Problem::getStatus, ProblemStatus.WITHDRAW, ProblemStatus.DRAFT)
        );
        // 如果都没这问题, 都不需要删除对应测试数据了
        if (deleteRows == 0){
            return ;
        }
        // 还需要获取删除对应测试数据
        List<ProblemTestData> testDataList = problemTestDataService.list(
                Wrappers.<ProblemTestData>lambdaQuery()
                        .eq(ProblemTestData::getProblemId, problemId)
        );
        problemTestDataService.removeBatchByIds(testDataList);
        testDataList.forEach(testData -> {
            Path path = Path.of(judgeProperties.getDataRoot(), testData.getStoragePath());
            FileUtil.del(path.getParent());
        });
    }
}
