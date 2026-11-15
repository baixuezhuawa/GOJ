package com.gusl.gojserver.problem.application;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.gojserver.problem.domain.model.Tag;
import com.gusl.gojserver.problem.application.vo.ProblemTagsVo;

import java.util.List;

public interface TagService extends IService<Tag> {

    /**
     * 根据问题id获取对应标签
     * @param problemId
     * @return
     */
    List<String> getTagByProblemId(Long problemId);

    List<ProblemTagsVo> getAllTags();

}
