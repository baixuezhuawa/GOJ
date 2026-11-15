package com.gusl.gojjudge.judging.engine;

import com.gusl.gojjudge.judging.model.CheckerConfig;
import com.gusl.gojjudge.judging.model.TestDataRef;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 判题请求，保存一次判题所需的材料和配置。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JudgeExecutionRequest {

    // 程序信息
    private String language;
    private String sourceCode;

    // 用户程序的运行限制
    private Long timeLimitMs;
    private Long memoryLimitKb;

    // 已经确定的数据集引用，不包含全部测试文件内容
    private TestDataRef testDataRef;

    // 普通、特殊或交互题
    private Integer problemType;

    // 文本比较或特殊校验所需的配置
    private CheckerConfig checkerConfig;
}