package com.gusl.gojserver.pojo.vo;

import lombok.Data;

/**
 * 题目标签查询结果，用于将当前页题目的标签批量组装到列表 VO。
 */
@Data
public class ProblemTagRow {

    /** 题目 id。 */
    private Long problemId;

    /** 标签名称。 */
    private String tagName;
}
