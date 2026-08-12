package com.gusl.gojjudge;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.gusl.gojjudge.adapter.lang.Java11LanguageAdapter;
import com.gusl.gojjudge.adapter.lang.Py3LanguageAdapter;
import com.gusl.gojjudge.client.GoJudgeClient;
import com.gusl.gojjudge.pojo.entity.CompilePlan;
import com.gusl.gojjudge.pojo.entity.ProgramArtifact;
import com.gusl.gojjudge.pojo.entity.RunContext;
import com.gusl.gojjudge.pojo.entity.RuntimeLimit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
class GojJudgeApplicationTests {

    @Autowired
    private GoJudgeClient client;

    @Autowired
    private Java11LanguageAdapter java11;

    @Autowired
    private Py3LanguageAdapter py3;

    @Test
    void testJava11(){
        String code = "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner scanner = new Scanner(System.in);\n        long a = scanner.nextLong();\n        long b = scanner.nextLong();\n      System.out.println(a + b);\n}       \n}";

        CompilePlan compilePlan = java11.createCompilePlan(code);

        JSONObject compileResult = client.run(compilePlan.getRequestBody()).getJSONObject(0);

        log.info("compile result:\n{}", compileResult);

        String fileId = compileResult.getJSONObject("fileIds").getString(java11.activeFileName());

        RunContext context = RunContext.builder()
                .input("1 2")
                .program(ProgramArtifact.cached(java11.activeFileName(), fileId))
                .limit(new RuntimeLimit(1_000, 128 *1024))
                .build();

        JSONObject runResult = client.run(java11.createRunRequest(context)).getJSONObject(0);

        log.info("run response:\n{}", runResult);
    }

    @Test
    void testPy3(){
        String code = "print(1 + 1)";
        RunContext context = RunContext.builder()
                .input("")
                .program(ProgramArtifact.inline(py3.activeFileName(), code))
                .limit(new RuntimeLimit(1_000, 128 * 1024))
                .build();
        JSONObject request = py3.createRunRequest(context);
        JSONArray response = client.run(request);
        JSONObject result = response.getJSONObject(0);

        log.info("response:\n{}", result);
    }

}
