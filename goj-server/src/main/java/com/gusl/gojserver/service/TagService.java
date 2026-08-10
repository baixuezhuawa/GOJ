package com.gusl.gojserver.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.gojserver.pojo.entity.Tag;

import java.util.List;

public interface TagService extends IService<Tag> {

    /**
     * 根据问题id获取对应标签
     * @param problemId
     * @return
     */
    List<String> getTagByProblemId(Long problemId);
}
