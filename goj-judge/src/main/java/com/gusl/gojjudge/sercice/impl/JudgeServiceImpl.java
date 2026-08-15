package com.gusl.gojjudge.sercice.impl;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.constant.SandBoxStatus;
import com.gusl.common.constant.SystemConstant;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.common.pojo.entity.ProblemTestData;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojjudge.adapter.AbstractLanguageAdapter;
import com.gusl.gojjudge.client.GoJudgeClient;
import com.gusl.gojjudge.exception.*;
import com.gusl.gojjudge.mapper.ProblemMapper;
import com.gusl.gojjudge.mapper.ProblemTestDataMapper;
import com.gusl.gojjudge.mapper.SubmissionMapper;
import com.gusl.gojjudge.pojo.entity.*;
import com.gusl.gojjudge.properties.JudgeProperties;
import com.gusl.gojjudge.sercice.JudgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 测评流程编排服务。
 *
 * <p>该类只负责组织测评业务：读取提交和题目数据、调用语言适配器生成请求、
 * 调用 go-judge 沙箱、比较输出、更新提交状态以及清理沙箱缓存。
 * 用户代码始终在 go-judge 沙箱中运行，Judge 进程本身不直接执行用户代码。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeServiceImpl implements JudgeService {

    /** 提交记录 Mapper，用于读取源码并更新测评状态和结果。 */
    private final SubmissionMapper submissionMapper;

    /** 题目 Mapper，用于读取运行时的时间和内存限制。 */
    private final ProblemMapper problemMapper;

    /** 测试数据 Mapper，用于读取题目的测试点数量。 */
    private final ProblemTestDataMapper problemTestDataMapper;

    /** go-judge HTTP 客户端；所有编译和运行请求都通过它进入沙箱。 */
    private final GoJudgeClient goJudgeClient;

    /** Judge 模块配置，包含测试数据根目录和沙箱地址。 */
    private final JudgeProperties judgeProperties;

    /** Spring 会自动注入所有 AbstractGoJudgeLanguageAdapter 的实现 Bean。 */
    private final List<AbstractLanguageAdapter> languageAdapters;



    @Override
    public void judge(Long taskId) {
        // 保持任务领取的原子性
        int updateEffectLines = submissionMapper.update(
                Wrappers.<Submission>lambdaUpdate()
                        .set(Submission::getStatus, JudgingConstant.WAIT)
                        .set(Submission::getJudgeStartTime, LocalDateTime.now())
                        .eq(Submission::getId, taskId)
                        .eq(Submission::getStatus, JudgingConstant.IN_QUEUE)
        );
        if(updateEffectLines == 0){
            log.info("taskId{}: 当前测评任务不存在或者已被处理", taskId);
            return ;
        }

        // 通过这个对象统一编译和运行状态的度量
        JudgeOutcome judgeOutcome = new JudgeOutcome();
        // 更新测评状态 等待
        judgeOutcome.setCurStatus(JudgingConstant.WAIT);
        Submission submission = submissionMapper.selectOne(
                Wrappers.<Submission>lambdaQuery()
                        .eq(Submission::getId, taskId)
                        .eq(Submission::getStatus, JudgingConstant.WAIT)
        );


        String fileId = null;

        try {
            // 获取语言适配器
            AbstractLanguageAdapter adapter = findAdapter(submission.getLanguage());

            // 加载问题, 测试数据信息
            Problem problem = queryProblem(submission.getProblemId());
            ProblemTestData testData = queryProblemTestData(submission.getProblemId());

            // 根据语言适配获取编译计划
            CompilePlan compilePlan = adapter.createCompilePlan(submission.getSourceCode());
            ProgramArtifact programArtifact;
            // 但是有些语言是不需要编译的
            if(compilePlan.isRequired()){
                // 更新测评状态 编译
                judgeOutcome.setCurStatus(JudgingConstant.COMPILE);
                updateSubmission(taskId, judgeOutcome);

                // 更新测评状态 (编译信息/编译错误信息)
                fileId = compile(compilePlan.getRequestBody(), compilePlan.getCachedArtifactName(), judgeOutcome);
                updateSubmission(taskId, judgeOutcome);

                programArtifact = ProgramArtifact.cached(adapter.activeFileName(), fileId);
            }else {
                // 脚本语言无需编译
                programArtifact = ProgramArtifact.inline(
                        adapter.activeFileName(),
                        submission.getSourceCode()
                );
            }

            // 更新测评状态 运行中
            judgeOutcome.setCurStatus(JudgingConstant.RUNNING);
            updateSubmission(taskId, judgeOutcome);

            runAllTestCase(adapter, problem, testData, programArtifact, judgeOutcome);

        } catch (CompileErrorException ce){
            log.info("taskId:{} 编译异常", taskId);
            judgeOutcome.setCurStatus(JudgingConstant.COMPILE_ERROR);
            judgeOutcome.setCompilerMsg(ce.getMessage());
        } catch (RuntimeErrorException re) {
            log.info("taskId:{} 运行时异常", taskId);
            judgeOutcome.setCurStatus(JudgingConstant.RUNTIME_ERROR);
            judgeOutcome.setJudgeMsg(re.getMessage());
        }catch (TLEException tle) {
            log.info("taskId{}: 时间超限", taskId);
            judgeOutcome.setCurStatus(JudgingConstant.TIME_LIMIT_EXCEEDED);
            judgeOutcome.setJudgeMsg(tle.getMessage());
        } catch (MLEException mle) {
            log.info("taskId{}: 内存超限", taskId);
            judgeOutcome.setCurStatus(JudgingConstant.MEMORY_LIMIT_EXCEEDED);
            judgeOutcome.setJudgeMsg(mle.getMessage());
        }catch (WAException wa){
            log.info("taskId{}: 答案错误", taskId);
            judgeOutcome.setCurStatus(JudgingConstant.WRONG_ANSWER);
            judgeOutcome.setJudgeMsg(wa.getMessage());
        }catch (Exception e){
            log.warn("任务{}: 测评异常, 结束测评", taskId, e);
            judgeOutcome.setCurStatus(SystemConstant.SYSTEM_ERROR);
            judgeOutcome.setJudgeMsg(e.getMessage());
        }finally {
            updateSubmission(taskId, judgeOutcome);
            deleteCacheCompileFile(fileId);
        }

    }

    private void updateSubmission(Long taskId, JudgeOutcome cur){
        submissionMapper.update(
                Wrappers.<Submission>lambdaUpdate()
                        .set(Submission::getStatus, cur.getCurStatus())
                        .set(cur.getTimeMs() != null, Submission::getTimeMs, cur.getTimeMs())
                        .set(cur.getMemoryKb() != null, Submission::getMemoryKb, cur.getMemoryKb())
                        .set(cur.getCompilerMsg() != null, Submission::getCompilerMsg, cur.getCompilerMsg())
                        .set(cur.getJudgeMsg() != null, Submission::getJudgeMsg, cur.getJudgeMsg())
                        .set(cur.getScore() != null, Submission::getScore, cur.getScore())
                        .set(Submission::getJudgeEndTime, LocalDateTime.now())
                        .eq(Submission::getId, taskId)
        );
    }

    private AbstractLanguageAdapter findAdapter(String language) {
        return languageAdapters.stream()
                .filter(adapter -> adapter.languageCode().equals(language))
                .findFirst()
                .orElseThrow(() -> new SystemErrorException("暂不支持该语言: " + language));
    }

    private Problem queryProblem(Long problemId){
        Problem problem = problemMapper.selectOne(
                Wrappers.<Problem>lambdaQuery()
                        .eq(Problem::getId, problemId)
                        .eq(Problem::getStatus, 1)
        );
        if (ObjectUtil.isEmpty(problem)){
            throw new SystemErrorException("无法加载问题");
        }
        return problem;
    }

    private ProblemTestData queryProblemTestData(Long problemId){
        ProblemTestData testData = problemTestDataMapper.selectOne(
                Wrappers.<ProblemTestData>lambdaQuery()
                        .eq(ProblemTestData::getProblemId, problemId)
                        .eq(ProblemTestData::getActive, 1)
        );
        if (ObjectUtil.isEmpty(testData)){
            throw new SystemErrorException("无法加载问题");
        }
        return testData;
    }

    private String compile(JSONObject request, String cachedArtifactName, JudgeOutcome cur){

        JSONArray response;

        try {
            response = goJudgeClient.run(request);
        } catch (Exception e) {
            cur.setCompilerMsg("系统异常, 编译失败");
            throw new SystemErrorException("系统异常, 编译失败");
        }

        if(response == null || response.isEmpty()){
            cur.setCompilerMsg("系统异常, 编译失败");
            throw new SystemErrorException("系统异常, 编译失败");
        }

        JSONObject result = response.getJSONObject(0);

        String staus = result.getString("status");
        Integer exitStatus = result.getInteger("exitStatus");

        JSONObject files = result.getJSONObject("files");
        String stdout = files.getString("stdout");
        String stderr = files.getString("stderr");


        if(SandBoxStatus.INTERNAL_ERROR.equals(staus) || SandBoxStatus.FILE_ERROR.equals(staus)){
            // TODO 需要上报管理员, 后续添加错误信息收集, 或者通过日志管理系统获取(需要重测)
            throw new SystemErrorException(staus);
        }
        if(
                SandBoxStatus.TIME_LIMIT_EXCEEDED.equals(staus) ||
                SandBoxStatus.SIGNALLED.equals(staus) ||
                SandBoxStatus.NONZERO_EXIT_STATUS.equals(staus) ||
                SandBoxStatus.MEMORY_LIMIT_EXCEEDED.equals(staus)
        ){
            throw new CompileErrorException(staus);
        }

        String compileMsg = String.format("stdout:\n%s\nstderr:\n%s", stdout, stderr);

        // 最后一层保险
        if(!SandBoxStatus.ACCEPTED.equals(staus) || exitStatus != 0){
            throw new CompileErrorException(compileMsg);
        }
        cur.setCompilerMsg(compileMsg);
        return result.getJSONObject("fileIds").getString(cachedArtifactName);
    }

    private void runAllTestCase(
            AbstractLanguageAdapter adapter,
            Problem problem,
            ProblemTestData problemTestData,
            ProgramArtifact programArtifact,
            JudgeOutcome cur
    ) {
        if(problemTestData.getTestNodeCount().equals(0)){
            // 一般这是正常上传逻辑的话, 不可能会出现这个问题的
            throw new SystemErrorException("测试数据异常: 测试点为空");
        }

        long maxTimeNs = 0L;
        long maxMemoryBytes = 0L;
        for(int testIndex = 1; testIndex <= problemTestData.getTestNodeCount(); testIndex++){

            // 加载测试输出输出数据, 保证标准输入和标准输出完整性, 否则抛出系统异常
            String inputData = loadTestData(problem.getId(), testIndex, "input");
            String outputData = loadTestData(problem.getId(), testIndex, "output");

            RunContext runContext = RunContext.builder()
                    .input(inputData)
                    .program(programArtifact)
                    .limit(new RuntimeLimit(problem.getTimeLimit(), problem.getMemoryLimit()))
                    .build();

            // 构造运行请求请求体
            JSONObject request = adapter.createRunRequest(runContext);

            // 运行测试样例, 如果下面这个方法抛异常的话当前runAllTestCase方法会继续往上抛吗?
            JSONObject result = runOneTestCase(request);

            String status = result.getString("status");
            Integer exitStatus = result.getInteger("exitStatus");

            JSONObject files = result.getJSONObject("files");
            String realOutput = files.getString("stdout");
            String stderr = files.getString("stderr");

            if(SandBoxStatus.SIGNALLED.equals(status) || SandBoxStatus.NONZERO_EXIT_STATUS.equals(status)){
                throw new RuntimeErrorException(status + ": " + stderr);
            }
            if(SandBoxStatus.TIME_LIMIT_EXCEEDED.equals(status)){
                throw new TLEException(status);
            }
            if(SandBoxStatus.MEMORY_LIMIT_EXCEEDED.equals(status)){
                throw new MLEException(status);
            }

            if(!SandBoxStatus.ACCEPTED.equals(status) || !exitStatus.equals(0)){
                throw new RuntimeException(stderr);
            }

            Long timeNs = result.getLong("time");
            Long memoryBytes = result.getLong("memory");
            maxTimeNs = Math.max(maxTimeNs, timeNs);
            maxMemoryBytes = Math.max(maxMemoryBytes, memoryBytes);

            cur.setTimeMs((int) ((maxTimeNs + 999_999L) / 1_000_000));
            cur.setMemoryKb((int) ((maxMemoryBytes + 1023) / 1024));

            boolean accepted = matchStdOutput(outputData, realOutput);

            // 目前如果不通过就先结束测评, 后续 IO模式 才考虑 score 的计算, 现在留着拓展空间.
            if(!accepted){
                throw new WAException("wrong answer one test" + testIndex);
            }
        }
        // 通过全部测试点
        cur.setCurStatus(JudgingConstant.ACCEPTED);
    }

    private String loadTestData(Long problemId, Integer testIndex, String inputOrOutput) {
        Path inputPath = Path.of(String.format(
                "%s\\p%s\\test%s\\%s.txt",
                judgeProperties.getDataRoot(),
                problemId,
                testIndex,
                inputOrOutput
        ));
        String data;
        try {
            data  = Files.readString(inputPath);
        } catch (IOException e) {
            throw new SystemErrorException("系统异常, 测试数据不完整");
        }
        return data;
    }

    private JSONObject runOneTestCase(JSONObject request){
        JSONObject result;
        try {
            JSONArray response = goJudgeClient.run(request);

            result = response.getJSONObject(0);

        } catch (Exception e) {
            throw new SystemErrorException("系统异常, 沙箱无响应");
        }
        return result;
    }

    private boolean matchStdOutput(String stdOutput, String realOutput){
        stdOutput = normalText(stdOutput);
        realOutput = normalText(realOutput);
        return stdOutput.equals(realOutput);
    }

    private String normalText(String text){
        return text
                .replace("\r\n", "\n")
                .stripTrailing();
    }

    private void deleteCacheCompileFile(String fileId){
        if(fileId == null){
            return;
        }
        try {
            goJudgeClient.deleteFile(fileId);
            log.info("fileId:{}, 删除成功", fileId);
        } catch (Exception e) {
            log.info("fileId:{} 删除失败, 系统错误", fileId);
        }
    }
}
