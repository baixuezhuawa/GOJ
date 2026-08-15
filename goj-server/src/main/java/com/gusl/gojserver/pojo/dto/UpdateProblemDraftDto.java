package com.gusl.gojserver.pojo.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UpdateProblemDraftDto extends ProblemDraftDto{

    // 相当于就只多了个问题 id, 第一次创建的时候, 并无id, 这次是更新就需要 id 进行更新
    private Long problemId;

}
