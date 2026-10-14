package com.gusl.gojjudge.judging.application.engine;

import com.gusl.gojjudge.judging.infrastructure.compiler.CompileResult;

import com.gusl.gojjudge.judging.infrastructure.language.AbstractLanguageAdapter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 判题执行上下文，保存选手程序与校验程序共用的本次运行信息。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JudgeContext {

    private JudgeExecutionRequest request;

    private AbstractLanguageAdapter adapter;

    private CompileResult compileResult;

    /** 特殊校验程序的语言适配器。 */
    private AbstractLanguageAdapter checkerAdapter;

    /** 特殊校验程序编译产物，由引擎统一清理。 */
    private CompileResult checkerCompileResult;

}
