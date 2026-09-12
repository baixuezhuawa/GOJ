package com.gusl.gojserver.pojo.vo;

import com.gusl.common.pojo.entity.JudgeTask;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class JudgeTaskDetailVo extends JudgeTask {

    private Long userId;

    private String userName;

    private Long problemId;

    private String problemName;

    private Long contestId;

    private String contestTitle;
}
