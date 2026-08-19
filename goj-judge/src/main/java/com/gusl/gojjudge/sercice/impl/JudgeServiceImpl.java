package com.gusl.gojjudge.sercice.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.constant.*;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.common.pojo.entity.ProblemReviewSubmission;
import com.gusl.common.pojo.entity.ProblemTestData;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojjudge.adapter.AbstractLanguageAdapter;
import com.gusl.gojjudge.client.GoJudgeClient;
import com.gusl.gojjudge.exception.*;
import com.gusl.gojjudge.mapper.ProblemMapper;
import com.gusl.gojjudge.mapper.ProblemReviewSubmissionMapper;
import com.gusl.gojjudge.mapper.ProblemTestDataMapper;
import com.gusl.gojjudge.mapper.SubmissionMapper;
import com.gusl.gojjudge.pojo.entity.*;
import com.gusl.gojjudge.sercice.JudgeService;
import com.gusl.gojjudge.sercice.SubmissionResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

    @Value("${goj.judge.data-root}")
    private String dataRoot;

    /**
     * 公共测评流程所需的题目和测试数据。
     * 使用 Java 16 引入的 record 作为不可变数据载体，避免测评材料在流程中被修改。
     */
    private record JudgeMaterial(Problem problem, ProblemTestData testData) {
    }

    /**
     * 提交记录 Mapper，用于读取源码并更新测评状态和结果。
     */
    private final SubmissionMapper submissionMapper;

    /**
     * 管理员验题提交 Mapper，用于读取验题源码并写回独立测评结果。
     */
    private final ProblemReviewSubmissionMapper reviewSubmissionMapper;

    /**
     * 题目 Mapper，用于读取运行时的时间和内存限制。
     */
    private final ProblemMapper problemMapper;

    /**
     * 测试数据 Mapper，用于读取题目的测试点数量。
     */
    private final ProblemTestDataMapper problemTestDataMapper;

    /**
     * go-judge HTTP 客户端；所有编译和运行请求都通过它进入沙箱。
     */
    private final GoJudgeClient goJudgeClient;

    /**
     * Spring 会自动注入所有 AbstractGoJudgeLanguageAdapter 的实现 Bean。
     */
    private final List<AbstractLanguageAdapter> languageAdapters;

    /**
     * 普通提交结果写回服务，负责终态幂等更新和派生统计维护。
     */
    private final SubmissionResultService submissionResultService;


    /**
     * 领取并执行普通用户提交。
     */
    @Override
    public void judgeSubmission(Long submissionId) {
       Submission submission = submissionMapper.selectById(submissionId);

       if(submission == null){
           throw new SystemErrorException("普通提交不存在: " + submissionId);
       }

        // 业务结果已经落库，说明可能只差 judge_task 的成功状态。
        if (JudgingConstant.TERMINAL_STATUSES.contains(submission.getStatus())) {
            log.info("普通提交已经完成，无需重复测评，{}", submissionId);
            return;
        }

        // judge_task 已经完成原子领取，这里只更新业务展示状态。
        submissionMapper.update(
                Wrappers.<Submission>lambdaUpdate()
                        .set(Submission::getStatus, JudgingConstant.WAIT)
                        .set(
                                submission.getJudgeStartTime() == null,
                                Submission::getJudgeStartTime,
                                LocalDateTime.now()
                        )
                        .eq(Submission::getId, submissionId)
        );

        // 把明确的普通提交数据交给公共测评流程。
        executeJudge(
                "普通提交 " + submissionId,
                submission.getLanguage(),
                submission.getSourceCode(),
                () -> loadPublishedMaterial(submission.getProblemId()),
                outcome -> submissionResultService.updateSubmission(submission, outcome)
        );
    }

    /**
     * 领取并执行管理员验题提交。
     */
    @Override
    public void judgeProblemReview(Long reviewSubmissionId) {
        // 加载管理员验题记录。
        ProblemReviewSubmission submission = reviewSubmissionMapper.selectById(reviewSubmissionId);

        if (submission == null) {
            throw new SystemErrorException("管理员验题提交不存在 " + reviewSubmissionId);
        }

        // 已经产生终态时不重复执行测评。
        if (JudgingConstant.TERMINAL_STATUSES.contains(submission.getStatus())) {
            log.info("管理员验题已经完成，无需重复测评 {}", reviewSubmissionId);
            return;
        }

        // 把明确的验题数据交给同一个公共测评流程。
        executeJudge(
                "管理员验题提交 " + reviewSubmissionId,
                submission.getLanguage(),
                submission.getSourceCode(),
                () -> loadProblemReviewMaterial(submission.getProblemId(), submission.getProblemTestDataId()),
                outcome -> updateProblemReviewSubmission(reviewSubmissionId, outcome)
        );
    }


    /**
     * 判断测评任务对应的业务记录是否已经进入终态。
     *
     * @param taskType 任务类型
     * @param businessId 业务记录 id
     * @return 是否已经进入终态
     */
    @Override
    public boolean isBusinessTerminal(String taskType, Long businessId) {
        if (JudgeTaskType.SUBMISSION.equals(taskType)) {
            Submission submission = submissionMapper.selectById(businessId);
            return submission != null
                    && JudgingConstant.TERMINAL_STATUSES.contains(submission.getStatus());
        }

        if (JudgeTaskType.PROBLEM_REVIEW.equals(taskType)) {
            ProblemReviewSubmission submission = reviewSubmissionMapper.selectById(businessId);
            return submission != null
                    && JudgingConstant.TERMINAL_STATUSES.contains(submission.getStatus());
        }

        throw new IllegalArgumentException("不支持的测评任务类型：" + taskType);
    }


    /**
     * 在测评任务死亡后写入业务记录的系统错误终态。
     */
    @Override
    public void markSystemError(String taskType, Long businessId, String errorMessage) {
        JudgeOutcome outcome = new JudgeOutcome();
        outcome.setCurStatus(SystemConstant.SYSTEM_ERROR);
        outcome.setJudgeMsg(errorMessage);

        // 普通测评
        if (JudgeTaskType.SUBMISSION.equals(taskType)) {
            Submission submission = submissionMapper.selectById(businessId);
            if (submission == null) {
                log.error("普通提交不存在，submissionId={}", businessId);
                return;
            }

            submissionResultService.updateSubmission(submission, outcome);
            return;
        }

        // 验题
        if (JudgeTaskType.PROBLEM_REVIEW.equals(taskType)) {
            // 因为是验题, 不需要更新有关用户做题数据的表
            updateProblemReviewSubmission(businessId, outcome);
            return;
        }

        log.error("无法写入系统错误，不支持的任务类型：{}", taskType);
    }


    /**
     * 执行两类任务共用的编译、运行、结果比较和异常映射流程。
     *
     * @param taskLabel      日志中的任务标识
     * @param language       语言编码
     * @param sourceCode     源代码
     * @param materialLoader 对应业务的数据加载器
     * @param outcomeUpdater 对应业务的状态写回器
     */
    private void executeJudge(
            String taskLabel,
            String language,
            String sourceCode,
            Supplier<JudgeMaterial> materialLoader,
            Consumer<JudgeOutcome> outcomeUpdater
    ) {
        JudgeOutcome judgeOutcome = new JudgeOutcome();
        judgeOutcome.setCurStatus(JudgingConstant.WAIT);
        String fileId = null;

        try {
            // 选择语言适配器，并由调用方提供的数据加载器获取明确的题目和测试数据。
            AbstractLanguageAdapter adapter = findAdapter(language);
            JudgeMaterial material = materialLoader.get();

            // 根据语言特性编译代码；脚本语言直接构造内联程序。
            CompilePlan compilePlan = adapter.createCompilePlan(sourceCode);
            ProgramArtifact programArtifact;
            if (compilePlan.isRequired()) {
                judgeOutcome.setCurStatus(JudgingConstant.COMPILE);
                outcomeUpdater.accept(judgeOutcome);

                fileId = compile(compilePlan.getRequestBody(), compilePlan.getCachedArtifactName(), judgeOutcome);
                outcomeUpdater.accept(judgeOutcome);

                programArtifact = ProgramArtifact.cached(adapter.activeFileName(), fileId);
            } else {
                programArtifact = ProgramArtifact.inline(adapter.activeFileName(), sourceCode);
            }

            // 使用同一套运行和输出比较逻辑执行全部测试点。
            judgeOutcome.setCurStatus(JudgingConstant.RUNNING);
            outcomeUpdater.accept(judgeOutcome);

            runAllTestCase(
                    adapter,
                    material.problem(),
                    material.testData(),
                    programArtifact,
                    judgeOutcome
            );
        } catch (CompileErrorException exception) {
            log.info("{} 编译失败", taskLabel);
            judgeOutcome.setCurStatus(JudgingConstant.COMPILE_ERROR);
            judgeOutcome.setCompilerMsg(exception.getMessage());

        } catch (RuntimeErrorException exception) {
            log.info("{} 运行时错误", taskLabel);
            judgeOutcome.setCurStatus(JudgingConstant.RUNTIME_ERROR);
            judgeOutcome.setJudgeMsg(exception.getMessage());

        } catch (TLEException exception) {
            log.info("{} 时间超限", taskLabel);
            judgeOutcome.setCurStatus(JudgingConstant.TIME_LIMIT_EXCEEDED);
            judgeOutcome.setJudgeMsg(exception.getMessage());

        } catch (MLEException exception) {
            log.info("{} 内存超限", taskLabel);
            judgeOutcome.setCurStatus(JudgingConstant.MEMORY_LIMIT_EXCEEDED);
            judgeOutcome.setJudgeMsg(exception.getMessage());

        } catch (WAException exception) {
            log.info("{} 答案错误", taskLabel);
            judgeOutcome.setCurStatus(JudgingConstant.WRONG_ANSWER);
            judgeOutcome.setJudgeMsg(exception.getMessage());

        } catch (SystemErrorException exception) {
            // 系统异常交给 JudgeTaskProcessor 决定重试或者死亡。
            log.warn("{} 发生测评系统异常", taskLabel, exception);
            throw exception;

        }catch (Exception exception){
            // 未知程序异常同样按照系统异常处理。
            log.error("{} 发生未知测评异常", taskLabel, exception);
            throw new SystemErrorException("未知测评系统异常：" + exception.getMessage());

        }finally {
            // 无论成功或失败，都写回各自业务表的终态并清理沙箱编译缓存。
            deleteCacheCompileFile(fileId);
        }
        outcomeUpdater.accept(judgeOutcome);
    }


    /**
     * 写回管理员验题提交状态。
     */
    private void updateProblemReviewSubmission(Long taskId, JudgeOutcome cur) {
        reviewSubmissionMapper.update(
                Wrappers.<ProblemReviewSubmission>lambdaUpdate()
                        .set(ProblemReviewSubmission::getStatus, cur.getCurStatus())
                        .set(cur.getTimeMs() != null, ProblemReviewSubmission::getTimeMs, cur.getTimeMs())
                        .set(cur.getMemoryKb() != null, ProblemReviewSubmission::getMemoryKb, cur.getMemoryKb())
                        .set(cur.getCompilerMsg() != null, ProblemReviewSubmission::getCompilerMsg, cur.getCompilerMsg())
                        .set(cur.getJudgeMsg() != null, ProblemReviewSubmission::getJudgeMsg, cur.getJudgeMsg())
                        .set(cur.getScore() != null, ProblemReviewSubmission::getScore, cur.getScore())
                        .set(
                                JudgingConstant.TERMINAL_STATUSES.contains(cur.getCurStatus()), // 最终结果才更新测评结束时间
                                ProblemReviewSubmission::getJudgeEndTime,
                                LocalDateTime.now()
                        )
                        .eq(ProblemReviewSubmission::getId, taskId)
        );
    }


    /**
     * 获取提交语言的对应适配器
     */
    private AbstractLanguageAdapter findAdapter(String language) {
        return languageAdapters.stream()
                .filter(adapter -> adapter.languageCode().equals(language))
                .findFirst()
                .orElseThrow(() -> new SystemErrorException("暂不支持该语言: " + language));
    }


    /**
     * 加载普通用户提交使用的已发布题目和正式测试数据。
     */
    private JudgeMaterial loadPublishedMaterial(Long problemId) {
        Problem problem = problemMapper.selectOne(
                Wrappers.<Problem>lambdaQuery()
                        .eq(Problem::getId, problemId)
                        .eq(Problem::getStatus, ProblemStatus.PUBLISH)
        );
        if (problem == null) {
            throw new SystemErrorException("无法加载已发布题目");
        }

        ProblemTestData testData = problemTestDataMapper.selectOne(
                Wrappers.<ProblemTestData>lambdaQuery()
                        .eq(ProblemTestData::getProblemId, problemId)
                        .eq(ProblemTestData::getStatus, ProblemTestDataStatus.READY)
                        .eq(ProblemTestData::getActive, true)
        );
        if (testData == null) {
            throw new SystemErrorException("无法加载正式测试数据");
        }
        return new JudgeMaterial(problem, testData);
    }


    /**
     * 加载管理员验题提交固定的待审核题目和测试数据集。
     */
    private JudgeMaterial loadProblemReviewMaterial(Long problemId, Long problemTestDataId) {
        Problem problem = problemMapper.selectOne(
                Wrappers.<Problem>lambdaQuery()
                        .eq(Problem::getId, problemId)
                        .eq(Problem::getStatus, ProblemStatus.PENDING)
        );
        if (problem == null) {
            throw new SystemErrorException("无法加载待审核题目");
        }

        ProblemTestData testData = problemTestDataMapper.selectOne(
                Wrappers.<ProblemTestData>lambdaQuery()
                        .eq(ProblemTestData::getId, problemTestDataId)
                        .eq(ProblemTestData::getProblemId, problemId)
                        .eq(ProblemTestData::getActive, false)
                        .eq(ProblemTestData::getStatus, ProblemTestDataStatus.EXTRACTED)
        );
        if (testData == null) {
            throw new SystemErrorException("无法加载待审核测试数据");
        }
        return new JudgeMaterial(problem, testData);
    }


    /**
     * 编译
     */
    private String compile(JSONObject request, String cachedArtifactName, JudgeOutcome cur) {

        JSONArray response;

        try {
            response = goJudgeClient.run(request);
        } catch (Exception e) {
            cur.setCompilerMsg("系统异常, 编译失败");
            throw new SystemErrorException("系统异常, 编译失败");
        }

        if (response == null || response.isEmpty()) {
            cur.setCompilerMsg("系统异常, 编译失败");
            throw new SystemErrorException("系统异常, 编译失败");
        }

        JSONObject result = response.getJSONObject(0);

        String staus = result.getString("status");
        Integer exitStatus = result.getInteger("exitStatus");

        JSONObject files = result.getJSONObject("files");
        String stdout = files.getString("stdout");
        String stderr = files.getString("stderr");


        if (SandBoxStatus.INTERNAL_ERROR.equals(staus) || SandBoxStatus.FILE_ERROR.equals(staus)) {
            throw new SystemErrorException(staus);
        }
        if (
                SandBoxStatus.TIME_LIMIT_EXCEEDED.equals(staus) ||
                        SandBoxStatus.SIGNALLED.equals(staus) ||
                        SandBoxStatus.NONZERO_EXIT_STATUS.equals(staus) ||
                        SandBoxStatus.MEMORY_LIMIT_EXCEEDED.equals(staus)
        ) {
            throw new CompileErrorException(staus);
        }

        String compileMsg = String.format("stdout:\n%s\nstderr:\n%s", stdout, stderr);

        // 最后一层保险
        if (!SandBoxStatus.ACCEPTED.equals(staus) || exitStatus != 0) {
            throw new CompileErrorException(compileMsg);
        }
        cur.setCompilerMsg(compileMsg);
        return result.getJSONObject("fileIds").getString(cachedArtifactName);
    }


    /**
     * 运行代码
     */
    private void runAllTestCase(
            AbstractLanguageAdapter adapter,
            Problem problem,
            ProblemTestData problemTestData,
            ProgramArtifact programArtifact,
            JudgeOutcome cur
    ) {
        if (problemTestData.getTestNodeCount() == null || problemTestData.getTestNodeCount() <= 0) {
            throw new SystemErrorException("测试数据异常: 测试点为空");
        }

        Path testDataDirectory = Path.of(dataRoot, problemTestData.getStoragePath());
        long maxTimeNs = 0L;
        long maxMemoryBytes = 0L;
        for (int testIndex = 1; testIndex <= problemTestData.getTestNodeCount(); testIndex++) {

            // 从当前数据集的 storagePath 加载输入和标准输出，staging 与正式目录共用该逻辑。
            String inputData = loadTestData(testDataDirectory, testIndex, "input");
            String outputData = loadTestData(testDataDirectory, testIndex, "output");

            RunContext runContext = RunContext.builder()
                    .input(inputData)
                    .program(programArtifact)
                    .limit(new RuntimeLimit(problem.getTimeLimit(), problem.getMemoryLimit()))
                    .build();

            // 构造运行请求请求体
            JSONObject request = adapter.createRunRequest(runContext);

            // 此处异常会继续向上传播，并由 judge 方法统一映射为提交终态。
            JSONObject result = runOneTestCase(request);

            String status = result.getString("status");
            Integer exitStatus = result.getInteger("exitStatus");

            JSONObject files = result.getJSONObject("files");
            String realOutput = files.getString("stdout");
            String stderr = files.getString("stderr");

            if (SandBoxStatus.SIGNALLED.equals(status) || SandBoxStatus.NONZERO_EXIT_STATUS.equals(status)) {
                throw new RuntimeErrorException(status + ": " + stderr);
            }
            if (SandBoxStatus.TIME_LIMIT_EXCEEDED.equals(status)) {
                throw new TLEException(status);
            }
            if (SandBoxStatus.MEMORY_LIMIT_EXCEEDED.equals(status)) {
                throw new MLEException(status);
            }

            if (!SandBoxStatus.ACCEPTED.equals(status) || !Integer.valueOf(0).equals(exitStatus)) {
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
            if (!accepted) {
                throw new WAException("wrong answer one test" + testIndex);
            }
        }
        // 通过全部测试点
        cur.setCurStatus(JudgingConstant.ACCEPTED);
    }


    /**
     * 读取一个测试点的输入或标准输出文件。
     */
    private String loadTestData(Path testDataDirectory, Integer testIndex, String inputOrOutput) {
        Path dataPath = testDataDirectory
                .resolve("test" + testIndex)
                .resolve(inputOrOutput + ".txt");
        try {
            return Files.readString(dataPath);
        } catch (IOException e) {
            throw new SystemErrorException("系统异常, 测试数据不完整");
        }
    }


    /**
     * 运行单个测试点
     */
    private JSONObject runOneTestCase(JSONObject request) {
        JSONObject result;
        try {
            JSONArray response = goJudgeClient.run(request);

            result = response.getJSONObject(0);

        } catch (Exception e) {
            throw new SystemErrorException("系统异常, 沙箱无响应");
        }
        return result;
    }


    /**
     * 比较输入输出
     */
    private boolean matchStdOutput(String stdOutput, String realOutput) {
        stdOutput = normalText(stdOutput);
        realOutput = normalText(realOutput);
        return stdOutput.equals(realOutput);
    }


    /**
     * 规范化输入输出
     */
    private String normalText(String text) {
        return text
                .replace("\r\n", "\n")
                .stripTrailing();
    }


    /**
     * 删除沙箱编译缓存文件
     */
    private void deleteCacheCompileFile(String fileId) {
        if (fileId == null) {
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
