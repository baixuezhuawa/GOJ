package com.gusl.gojserver.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.gojserver.pojo.dto.ProblemPageListDto;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.gojserver.pojo.vo.ProblemInfoVo;
import com.gusl.gojserver.pojo.vo.ProblemPageListVo;

import java.util.List;

public interface ProblemService extends IService<Problem> {

    List<ProblemPageListVo> getProblemList(ProblemPageListDto dto);

    ProblemInfoVo getProblemInfoById(Long id);
}
