package com.gusl.gojjudge.judging.runner;

import com.gusl.gojjudge.judging.sandbox.GoJudgeExecuter;

import com.alibaba.fastjson2.JSONObject;
import com.gusl.gojjudge.judging.language.AbstractLanguageAdapter;
import com.gusl.gojjudge.judging.sandbox.SandBoxResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Runner {

    private final GoJudgeExecuter goJudgeExecuter;

    public RunResult run(AbstractLanguageAdapter adapter, RunContext context){

        RunResult runResult = new RunResult();

        JSONObject request = adapter.createRunRequest(context);

        try {
            SandBoxResult sandBoxResult = goJudgeExecuter.execute(request);


            runResult.setStatus(sandBoxResult.getStatus());

            runResult.setExitStatus(sandBoxResult.getExitStatus());

            runResult.setTimeNan(sandBoxResult.getTime());

            runResult.setMemoryByte(sandBoxResult.getMemory());

            runResult.setRunTimeNan(sandBoxResult.getRunTime());

            runResult.setStdout(sandBoxResult.getFiles().get("stdout"));

            runResult.setStderr(sandBoxResult.getFiles().get("stderr"));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return runResult;
    }

}
