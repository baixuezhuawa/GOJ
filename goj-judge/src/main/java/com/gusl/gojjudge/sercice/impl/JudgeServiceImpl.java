package com.gusl.gojjudge.sercice.impl;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.common.pojo.entity.ProblemTestData;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojjudge.client.GoJudgeClient;
import com.gusl.gojjudge.mapper.ProblemMapper;
import com.gusl.gojjudge.mapper.ProblemTestDataMapper;
import com.gusl.gojjudge.mapper.SubmissionMapper;
import com.gusl.gojjudge.properties.JudgeProperties;
import com.gusl.gojjudge.properties.lang.Java11;
import com.gusl.gojjudge.sercice.JudgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeServiceImpl implements JudgeService {

    private final SubmissionMapper submissionMapper;
    private final ProblemMapper problemMapper;
    private final ProblemTestDataMapper testDataMapper;
    private final GoJudgeClient goJudgeClient;
    private final Java11 java11;
    private final JudgeProperties judgeProperties;

    // TODO 即使失败也是需要更改submission表的状态的, 这个后续需要处理
    /**
     * 测评任务
     * @param taskId 带测评的submissionId
     */
    @Override
    public void judge(Long taskId) throws IOException {
        Submission submission = submissionMapper.selectById(taskId);
        // 如果这个提交不存在或不在"in queue"状态则结束这次任务
        if(ObjectUtil.isEmpty(submission) || !JudgingConstant.IN_QUEUE.equals(submission.getStatus())){
            log.info("id:{} 提交, 已经结束或者已经不存在! 任务结束", taskId);
            return ;
        }

        log.info("开始测评...");

        String fileId = null;
        try {

            // TODO 是不是可以不用这个表来读取状态, 不然查询次数有些多了, 但是好像不可避免, 想想好的解决方案
            // 更新测评状态 编译中
            updateSubmission(JudgingConstant.COMPILE, taskId);

            // 加载问题, 测试数据路径信息
            Problem problem = problemMapper.selectById(submission.getProblemId());
            ProblemTestData testDataInfo = testDataMapper.selectOne(
                    Wrappers.<ProblemTestData>lambdaQuery()
                            .eq(ProblemTestData::getProblemId, problem.getId())
            );

            // 构造编译请求请求体
            JSONObject compileRequest = crateCompileRequestBody(submission.getSourceCode());

            // 利用openfeign发送请求到沙箱进行测评, 获取编译结果, 缓存文件fileId
            JSONArray compileResponse;
            try {
                compileResponse = goJudgeClient.run(compileRequest);
            }catch (Exception e){
                log.error("调用沙箱编译失败，submissionId={}", submission.getId(), e);
                // TODO 应该需要抛一个异常或者啥的, 然后处理失败后续的处理
                return ;
            }

            // 处理返回的编译结果信息
            JSONObject compileRes = compileResponse.getJSONObject(0);

            String status = compileRes.getString("status");
            Integer exitStatus = compileRes.getInteger("exitStatus");
            if(!"Accepted".equals(status) || exitStatus == null || exitStatus != 0){
                log.info("编译失败---submissionId:{} result:{}", taskId, compileResponse);
                updateSubmission(JudgingConstant.ERROR, taskId);
                // TODO 应该需要抛一个异常或者啥的, 然后处理失败后续的处理
                return ;
            }

            // 获取编译缓存文件id
            JSONObject fileIds = compileRes.getJSONObject("fileIds");
            if (fileIds == null) {
                log.info("编译成功但没有返回 fileIds: {}", JSON.toJSONString(compileResponse));
                updateSubmission(JudgingConstant.ERROR, taskId);
                return;
            }
            fileId = fileIds.getString("Main.jar");
            if (fileId == null || fileId.isBlank()) {
                log.info("没有获取到 Main.jar 的 fileId: {}", JSON.toJSONString(compileResponse));
                updateSubmission(JudgingConstant.ERROR, taskId);
                return;
            }

            // 编译成功, 需要开始执行测试点任务了
            // 更新测评状态 运行中
            // TODO 后续再升级可以动态响应运行到的测试点情况, 不至于只是 in queue -> compiling -> running -> result
            submissionMapper.update(Wrappers.<Submission>lambdaUpdate()
                    .set(Submission::getStatus, JudgingConstant.RUNNING)
                    .set(Submission::getJudgeStartTime, LocalDateTime.now())
                    .eq(Submission::getId, taskId)
            );

            // 串行执行任务
            boolean testResult = true;
            for(int i = 1; i <= testDataInfo.getTestNodeCount(); i++){
                // 当前任务的数据格目录/task{i}
                Path inputPath = Path.of(String.format("%s/p%d/test%d/input.txt",
                            judgeProperties.getDataRoot(),
                            testDataInfo.getProblemId(),
                            i
                        ));
                Path outputPath = Path.of(String.format("%s/p%d/test%d/output.txt",
                        judgeProperties.getDataRoot(),
                        testDataInfo.getProblemId(),
                        i
                ));
                // 载入测试数据
                String inputText = Files.readString(inputPath, StandardCharsets.UTF_8);

                JSONObject runRequest = createRunRequestBody(inputText, fileId);

                JSONArray runResponse = runTask(runRequest);

                if(runResponse == null){
                    testResult = false;
                    log.info("测评点错误");
                    break;
                }

                JSONObject runRes = runResponse.getJSONObject(0);

                // 实际输出和期望输出进行比较
                String actualOutput = getFileContent(runRes, "stdout");
                String expectedOutput = Files.readString(outputPath, StandardCharsets.UTF_8);
                actualOutput = actualOutput.replace("\r\n", "\n").stripTrailing();
                expectedOutput = expectedOutput.replace("\r\n", "\n").stripTrailing();

                testResult = actualOutput.equals(expectedOutput);

                if(!testResult) {
                    log.info("测试点错误");
                    break;
                }

                log.info("running - submission:{} problem:{} task{} Accepted", taskId, problem.getId(), i);
            }


            log.info("submission:{} 测评结束", taskId);
            // 更新测评表, 还要增加 执行耗时, 空间占用
            submissionMapper.update(Wrappers.<Submission>lambdaUpdate()
                            .set(Submission::getStatus, JudgingConstant.RUNNING)
                            .set(Submission::getJudgeEndTime, LocalDateTime.now())
                            .set(Submission::getTimeMs, 0)  // TODO 待修改
                            .set(Submission::getMemoryKb, 0) // TODO 待修改
                    // 这个时候状态就是最终ac结果了, 或者从score判断, 如果是满分前端响应ac
                            .set(Submission::getStatus, testResult ? "Accepted" : "WrongAnswer")
                    .eq(Submission::getId, taskId)
            );

        } finally {
            deleteCachedFile(taskId, fileId);
            log.info("结束测评...");
        }
    }

    /**
     * 删除编译阶段由沙箱生成的缓存文件。清理失败不能覆盖原判题结果。
     */
    private void deleteCachedFile(Long taskId, String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return;
        }

        try {
            goJudgeClient.deleteFile(fileId);
            log.debug("已删除沙箱缓存文件: submissionId={}, fileId={}", taskId, fileId);
        } catch (Exception e) {
            log.warn("删除沙箱缓存文件失败: submissionId={}, fileId={}", taskId, fileId, e);
        }
    }

    /**
     * 获取运行请求请求体 TODO 固定写死 Java 语言的, 后续带改进
     * @param testData 测试数据
     * @param fileId 编文件缓存id
     * @return 运行请求请求体
     */
    private JSONObject createRunRequestBody(String testData, String fileId){
        JSONObject cmd = new JSONObject();

        cmd.put("args", List.of(
               java11.getJava(),
                "-Dfile.encoding=UTF-8",
                "-cp",
                "/w/Main.jar",
                "Main"
        ));

        // 运行阶段显式设置资源限制，避免使用沙箱默认的极小内存限制。
        // go-judge 的 CPU 限制单位是纳秒，内存/栈限制单位是字节。
        cmd.put("cpuLimit", 3_000_000_000L);        // 最多使用 3 秒 CPU 时间
        cmd.put("realCpuLimit", 9_000_000_000L);    // 墙钟时间上限 9 秒
        cmd.put("memoryLimit", 256L * 1024 * 1024); // Java 程序允许使用 256 MB
        cmd.put("stackLimit", 128L * 1024 * 1024);  // 栈限制 128 MB
        cmd.put("procLimit", 64);                   // 限制程序进程数量

        JSONObject content = new JSONObject();
        content.put("content", testData);
        JSONObject stdout = new JSONObject();
        stdout.put("name", "stdout");
        stdout.put("max", 1048576);
        JSONObject stderr = new JSONObject();
        stderr.put("name", "stderr");
        stderr.put("max", 16777216);
        cmd.put("files", List.of(content, stdout, stderr));

        JSONObject copyIn = new JSONObject();
        JSONObject mainJar = new JSONObject();
        mainJar.put("fileId", fileId);
        copyIn.put("Main.jar", mainJar);
        cmd.put("copyIn", copyIn);

        cmd.put("copyOut", List.of("stdout", "stderr"));

        JSONObject request = new JSONObject();
        request.put("cmd", List.of(cmd));
        return request;
    }

    /**
     * 获取编译请求请求体 TODO 固定写死 Java 语言的, 后续带改进
     * @param sourceCode 源代码
     * @return 请求体
     */
    private JSONObject crateCompileRequestBody(String sourceCode){
        JSONObject cmd = new JSONObject();

        cmd.put("args", List.of(
                "/bin/bash",
                "-c",
                java11.getJavac()
                        + " -encoding UTF-8 Main.java &&"
                        + " /usr/local/java/jdk11/bin/jar -cf Main.jar *.class"
        ));

        cmd.put("env", List.of("PATH=/usr/local/java/jdk11/bin:/usr/bin:/bin"));

        // 编译器也运行在沙箱内；不设置这些字段会落到沙箱默认的极小内存限制。
        // javac/jar 需要比用户程序更宽的资源，避免编译器自身被误判为 MLE。
        cmd.put("cpuLimit", 10_000_000_000L);       // 编译最多使用 10 秒 CPU 时间
        cmd.put("realCpuLimit", 30_000_000_000L);   // 墙钟时间上限 30 秒
        cmd.put("memoryLimit", 512L * 1024 * 1024); // javac/jar 允许使用 512 MB
        cmd.put("stackLimit", 128L * 1024 * 1024);  // 栈限制 128 MB
        cmd.put("procLimit", 64);                   // 限制编译进程及其子进程数量

        JSONObject stdin = new JSONObject();
        stdin.put("content", "");

        JSONObject stdout = new JSONObject();
        stdout.put("name", "stdout");
        stdout.put("max", 16 * 1024 * 1024);

        JSONObject stderr = new JSONObject();
        stderr.put("name", "stderr");
        stderr.put("max", 16 * 1024 * 1024);

        cmd.put("files", List.of(stdin, stdout, stderr));

        JSONObject sourceFile = new JSONObject();
        sourceFile.put("content", sourceCode);

        JSONObject copyIn = new JSONObject();
        copyIn.put("Main.java", sourceFile);

        cmd.put("copyIn", copyIn);
        cmd.put("copyOut", List.of("stdout", "stderr"));
        cmd.put("copyOutCached", List.of("Main.jar"));

        JSONObject request = new JSONObject();
        request.put("cmd", List.of(cmd));

        return request;
    }

    /**
     * 运行任务
     * @param query 运行请求
     * @return 测试点的测评结果
     */
    private JSONArray runTask(JSONObject query){
        // 或者在这里构造请求?
        // 但是感觉请求有很多可以服用的东西
        try {
            JSONArray cmdResponse = goJudgeClient.run(query);
            JSONObject cmdRes = cmdResponse.getJSONObject(0);
            String status = cmdRes.getString("status");
            Integer exitStatus = cmdRes.getInteger("exitStatus");

            if(!"Accepted".equals(status) || exitStatus == null || exitStatus != 0){
                log.info("程序运行失败: {}", JSON.toJSONString(cmdRes));
                return cmdResponse;
            }
            String stdout = getFileContent(cmdRes, "stdout");
            String stderr = getFileContent(cmdRes, "stderr");

            log.info("运行成功: {}", cmdRes);
            return cmdResponse;
        } catch (Exception e) {
            log.warn("沙箱请求失败或者超时, 放弃本次测评", e);
            return null;
        }
    }

    /**
     * 从沙箱运行后返回的结果中提取文本
     * @param res 运行结果json对象
     * @param tar 目标文本字段
     * @return 文本
     */
    private String getFileContent(JSONObject res, String tar){
        JSONObject files = res.getJSONObject("files");
        return files == null ? "" :files.getString(tar);
    }

    private void updateSubmission(String status, Long taskId){
        // 更新测评状态 编译中
        submissionMapper.update(Wrappers.<Submission>lambdaUpdate()
                .set(Submission::getStatus, status)
                .eq(Submission::getId, taskId)
        );
    }
}
