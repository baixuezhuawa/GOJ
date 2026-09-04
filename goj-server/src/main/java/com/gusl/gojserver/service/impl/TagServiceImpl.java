package com.gusl.gojserver.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.gojserver.mapper.TagMapper;
import com.gusl.gojserver.pojo.entity.Tag;
import com.gusl.gojserver.pojo.vo.ProblemTagsVo;
import com.gusl.gojserver.service.TagService;
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
