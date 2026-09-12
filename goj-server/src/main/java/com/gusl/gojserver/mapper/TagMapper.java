package com.gusl.gojserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gusl.gojserver.pojo.entity.Tag;
import com.gusl.gojserver.pojo.vo.ProblemTagRow;
import com.gusl.gojserver.pojo.vo.ProblemTagsVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /**
     * 通过问题id获取对应标签
     * @param problemId 问题id
     * @return 标签集合
     */
    List<String> getTagByProblemId(@Param("problemId") Long problemId);

    /**
     * 批量获取题目标签。
     *
     * @param problemIds 题目 id 集合
     * @return ProblemTagRow
     */
    List<ProblemTagRow> getTagsByProblemIds(@Param("problemIds") List<Long> problemIds);

    /**
     * 获取TagVo, 包含 id, name
     * @param problemId 问题id
     * @return ProblemTagsVo
     */
    List<ProblemTagsVo> getTagsByProblemId(@Param("problemId") Long problemId);

    void addTagToProblem(@Param("problemId") Long problemId, @Param("tagId") Long tagId);


    void deleteProblemTag(@Param("problemId") Long problemId, @Param("tagId") Long tagId);
}
