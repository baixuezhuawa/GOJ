package com.gusl.gojjudge.pojo.entity;

import com.gusl.gojjudge.adapter.AbstractLanguageAdapter;
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
