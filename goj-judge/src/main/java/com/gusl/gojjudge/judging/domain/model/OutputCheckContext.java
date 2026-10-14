package com.gusl.gojjudge.judging.domain.model;

import com.gusl.gojjudge.judging.infrastructure.compiler.CompileResult;
import com.gusl.gojjudge.judging.infrastructure.language.AbstractLanguageAdapter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 输出校验参数，封装测试点内容、校验程序和运行限制。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutputCheckContext {

    /** 当前测试点输入。 */
    private String input;

    /** output.txt 中的预期输出。 */
    private String expectedOutput;

    /** 选手程序输出，作为特殊校验程序的标准输入。 */
    private String actualOutput;

    /** 校验程序语言适配器，普通文本比对不使用。 */
    private AbstractLanguageAdapter checkerAdapter;

    /** 本次判题共享的校验程序编译产物。 */
    private CompileResult checkerCompileResult;

    /** 校验程序时间限制，单位毫秒，沿用题目限制。 */
    private Long timeLimitMs;

    /** 校验程序内存限制，单位 KB，沿用题目限制。 */
    private Long memoryLimitKb;
}
