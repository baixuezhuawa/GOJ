package com.gusl.gojjudge.judging.application.engine;

import com.gusl.gojjudge.judging.infrastructure.compiler.CompileResult;

import com.gusl.gojjudge.judging.infrastructure.language.AbstractLanguageAdapter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JudgeContext {

    private JudgeExecutionRequest request;

    private AbstractLanguageAdapter adapter;

    private CompileResult compileResult;

}
