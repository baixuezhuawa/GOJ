package com.gusl.gojjudge.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TestCase {

    /** 测试点 */
    private int testCaseNo;

    /** 输入 */
    private String input;

    /** 精确匹配模式下的输出 */
    private String expectedOutput;

}
