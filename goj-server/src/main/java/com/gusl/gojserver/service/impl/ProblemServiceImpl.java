package com.gusl.gojserver.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.common.utils.StringUtils;
import com.gusl.gojserver.mapper.ProblemMapper;
import com.gusl.gojserver.mapper.TagMapper;
import com.gusl.gojserver.pojo.dto.ProblemPageListDto;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.gojserver.pojo.vo.ProblemInfoVo;
import com.gusl.gojserver.pojo.vo.ProblemPageListVo;
import com.gusl.gojserver.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProblemServiceImpl extends ServiceImpl<ProblemMapper, Problem> implements ProblemService {

    private final ProblemMapper problemMapper;
    private final TagMapper tagMapper;

    /**
     * 分页条件查询题目列表
     * @param dto 分页查询条件
     * @return 题目列表vo
     */
    @Override
    public List<ProblemPageListVo> getProblemList(ProblemPageListDto dto) {
        // 设置查询条件, 分页信息
        LambdaQueryWrapper<Problem> query = new LambdaQueryWrapper<>();
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

    /**
     * 根据 题目id 获取题目详细信息
     * @param id 题目id
     * @return 题目详细信息
     */
    @Override
    public ProblemInfoVo getProblemInfoById(Long id) {
        ProblemInfoVo info= new ProblemInfoVo();
        Problem problem = getOne(Wrappers.<Problem>lambdaQuery().eq(Problem::getId, id));
        // 进行属性卡拷贝
        BeanUtil.copyProperties(problem, info);
        info.setTags(tagMapper.getTagByProblemId(id));
        return info;
    }
}
