package com.gusl.gojserver.problem.application;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.gojserver.problem.infrastructure.persistence.TagMapper;
import com.gusl.gojserver.problem.domain.model.Tag;
import com.gusl.gojserver.problem.application.vo.ProblemTagsVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    private final TagMapper tagMapper;

    /**
     * 根据问题id获取对应标签
     * @param problemId 问题id
     * @return 标签集合
     */
    @Override
    public List<String> getTagByProblemId(Long problemId) {
        return tagMapper.getTagByProblemId(problemId);
    }

    @Override
    public List<ProblemTagsVo> getAllTags() {
        List<Tag> tags = tagMapper.selectList(Wrappers.<Tag>lambdaQuery());
        List<ProblemTagsVo> res = new ArrayList<>();
        tags.forEach(tag -> {
            res.add(new ProblemTagsVo(tag.getId(), tag.getTagName()));
        });
        return res;
    }
}
