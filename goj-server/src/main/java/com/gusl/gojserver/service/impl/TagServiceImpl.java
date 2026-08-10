package com.gusl.gojserver.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.gojserver.mapper.TagMapper;
import com.gusl.gojserver.pojo.entity.Tag;
import com.gusl.gojserver.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
