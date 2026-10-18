package com.gusl.gojjudge.judging;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.constant.*;
import com.gusl.gojjudge.judging.engine.JudgeRequestConfigurer;
import com.gusl.gojjudge.judging.model.TestDataRef;
import com.gusl.gojjudge.judging.mapper.ProblemMapper;
import com.gusl.gojjudge.judging.mapper.ProblemTestDataMapper;
import com.gusl.gojjudge.judging.mapper.ContestProblemMapper;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojjudge.judging.exception.JudgeSystemException;
import com.gusl.gojjudge.judging.mapper.ContestSubmissionMapper;
import com.gusl.gojjudge.judging.mapper.ProblemReviewSubmissionMapper;
import com.gusl.gojjudge.judging.mapper.SubmissionMapper;
import com.gusl.gojjudge.judging.engine.JudgeExecutionRequest;
import com.gusl.gojjudge.judging.engine.JudgeEngine;
import com.gusl.gojjudge.judging.engine.JudgeSession;
import com.gusl.gojjudge.judging.result.SubmissionResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 测评准备服务，按提交类型加载材料并给 JudgeSession 绑定业务回写方法。
 * 判题过程交给 JudgeEngine，数据库更新与事务交给结果服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeServiceImpl implements JudgeService {

    /**
     * 提交记录 Mapper，用于读取源码并更新测评状态和结果。
     */
    private final SubmissionMapper submissionMapper;

    /**
     * 管理员验题提交 Mapper，用于读取验题源码并写回独立测评结果。
     */
    private final ProblemReviewSubmissionMapper reviewSubmissionMapper;

    /** 比赛提交记录 Mapper */
    private final ContestSubmissionMapper contestSubmissionMapper;

    /**
     * 普通提交结果写回服务，负责终态幂等更新和派生统计维护。
     */
    private final SubmissionResultService submissionResultService;

    private final ProblemMapper problemMapper;
    private final ProblemTestDataMapper problemTestDataMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final JudgeRequestConfigurer judgeRequestConfigurer;

    private final JudgeEngine judgeEngine;


    /**
     * 执行测评
     * @param message 任务信息
     */
    @Override
    public void judge(JudgeTaskMessage message){
        // 不同提交只在材料来源和回写方法上分流，直接准备本次会话。
        // 在里面准备 语言, 提交代码, 时空限制, 测试数据信息, 更新方法.
        JudgeSession cur = switch (message.getTaskType()) {
            case JudgeTaskType.SUBMISSION -> prepareSubmission(message);
            case JudgeTaskType.CONTEST_SUBMISSION -> prepareContestSubmission(message);
            case JudgeTaskType.PROBLEM_REVIEW -> prepareProblemReview(message);
            case null, default -> throw new JudgeSystemException("不支持的测评任务类型：" + message.getTaskType());
        };
        // 准备方法返回 null 表示业务已经结束，不再重复测评。
        if (cur == null) {
            return;
        }

        try {
            // 材料准备完毕，写入等待执行状态
            cur.status(JudgingConstant.WAIT).push();

            // 引擎直接操作 cur，最终结果也由引擎 push
            judgeEngine.judge(cur);

            // 防止引擎遗漏终态写回，却让 Processor 宣布任务成功
            if (!cur.isFinished()) {
                throw new JudgeSystemException("判题引擎返回时尚未写入终态");
            }
        } catch (Exception exception) {
            // 关闭本次会话，异常继续交给 Processor
            cur.dead();
            throw exception;
        }
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
            return submission != null && JudgingConstant.TERMINAL_STATUSES.contains(submission.getStatus());
        }

        if (JudgeTaskType.PROBLEM_REVIEW.equals(taskType)) {
            ProblemReviewSubmission submission = reviewSubmissionMapper.selectById(businessId);
            return submission != null && JudgingConstant.TERMINAL_STATUSES.contains(submission.getStatus());
        }

        if (JudgeTaskType.CONTEST_SUBMISSION.equals(taskType)) {
            ContestSubmission submission = contestSubmissionMapper.selectById(businessId);
            return submission != null && JudgingConstant.TERMINAL_STATUSES.contains(submission.getStatus());
        }

        throw new IllegalArgumentException("不支持的测评任务类型：" + taskType);
    }


    /** 准备普通提交，读取当前启用数据并绑定普通提交更新方法。 */
    private JudgeSession prepareSubmission(JudgeTaskMessage message) {
        JudgeExecutionRequest request = new JudgeExecutionRequest();

        Submission submission = submissionMapper.selectById(message.getBusinessId());
        if (submission == null) {
            throw new JudgeSystemException("普通提交不存在: " + message.getBusinessId());
        }
        if (JudgingConstant.TERMINAL_STATUSES.contains(submission.getStatus())) {
            return null;
        }
        Problem problem = problemMapper.selectById(submission.getProblemId());

        if (problem == null) {
            throw new JudgeSystemException("题目已不存在");
        }

        request.setLanguage(submission.getLanguage());
        request.setSourceCode(submission.getSourceCode());
        request.setTimeLimitMs(problem.getTimeLimit().longValue());
        request.setMemoryLimitKb(problem.getMemoryLimit().longValue());

        judgeRequestConfigurer.configure(problem, request);

        ProblemTestData testData = problemTestDataMapper.selectOne(
                Wrappers.<ProblemTestData>lambdaQuery()
                        .eq(ProblemTestData::getProblemId, problem.getId())
                        .eq(ProblemTestData::getActive, 1)
        );
        if (testData == null) {
            throw new JudgeSystemException("题目没有已启用的测试数据");
        }
        TestDataRef testDataRef = TestDataRef.builder()
                .problemId(problem.getId())
                .storagePath(testData.getStoragePath())
                .storageType(StorageType.LOCAL_FILE)
                .testCaseCount(testData.getTestNodeCount())
                .testDataId(testData.getId())
                .version(testData.getVersion())
                .build();
        request.setTestDataRef(testDataRef);

        // 直接绑定结果服务，push 时在该服务的事务中更新主表和关联数据。
        JudgeSession session = new JudgeSession(message,
                (task, outcome) -> submissionResultService.updateSubmission(submission, outcome));
        session.setRequest(request);
        return session;
    }

    /** 准备比赛提交，使用比赛绑定的数据集并绑定比赛结果更新方法。 */
    private JudgeSession prepareContestSubmission(JudgeTaskMessage message) {
        ContestSubmission submission = contestSubmissionMapper.selectById(message.getBusinessId());
        if (submission == null) {
            throw new JudgeSystemException("比赛提交不存在: " + message.getBusinessId());
        }
        if (JudgingConstant.TERMINAL_STATUSES.contains(submission.getStatus())) {
            return null;
        }

        ContestProblem contestProblem = contestProblemMapper.selectOne(
                Wrappers.<ContestProblem>lambdaQuery()
                        .eq(ContestProblem::getContestId, submission.getContestId())
                        .eq(ContestProblem::getProblemId, submission.getProblemId())
                        .eq(ContestProblem::getReleaseStatus, ContestProblemStatus.OPENING));
        if (contestProblem == null) {
            throw new JudgeSystemException("该比赛题目不存在或未启用");
        }
        Problem problem = problemMapper.selectById(submission.getProblemId());
        ProblemTestData testData = problemTestDataMapper.selectById(contestProblem.getTestDataId());
        if (problem == null || testData == null) {
            throw new JudgeSystemException("无法加载比赛题目或测试数据");
        }

        JudgeExecutionRequest request = JudgeExecutionRequest.builder()
                .language(submission.getLanguage())
                .sourceCode(submission.getSourceCode())
                .timeLimitMs(problem.getTimeLimit().longValue())
                .memoryLimitKb(problem.getMemoryLimit().longValue())
                .testDataRef(TestDataRef.builder()
                        .problemId(problem.getId()).testDataId(testData.getId())
                        .storagePath(testData.getStoragePath()).storageType(StorageType.LOCAL_FILE)
                        .testCaseCount(testData.getTestNodeCount()).version(testData.getVersion()).build())
                .build();
        judgeRequestConfigurer.configure(problem, request);
        // 直接绑定结果服务，push 时在该服务的事务中更新主表和关联数据。
        JudgeSession session = new JudgeSession(message,
                (task, outcome) -> submissionResultService.updateSubmission(submission, outcome));
        session.setRequest(request);
        return session;
    }

    /** 准备管理员验题，允许读取指定的未启用数据集。 */
    private JudgeSession prepareProblemReview(JudgeTaskMessage message) {
        ProblemReviewSubmission submission = reviewSubmissionMapper.selectById(message.getBusinessId());
        if (submission == null) {
            throw new JudgeSystemException("验题提交不存在: " + message.getBusinessId());
        }
        if (JudgingConstant.TERMINAL_STATUSES.contains(submission.getStatus())) {
            return null;
        }
        Problem problem = problemMapper.selectById(submission.getProblemId());
        ProblemTestData testData = problemTestDataMapper.selectOne(Wrappers.<ProblemTestData>lambdaQuery()
                .eq(ProblemTestData::getId, submission.getProblemTestDataId())
                .eq(ProblemTestData::getProblemId, submission.getProblemId()));
        if (problem == null || testData == null) {
            throw new JudgeSystemException("题目或测试数据已不存在");
        }
        JudgeExecutionRequest request = JudgeExecutionRequest.builder()
                .language(submission.getLanguage()).sourceCode(submission.getSourceCode())
                .timeLimitMs(problem.getTimeLimit().longValue()).memoryLimitKb(problem.getMemoryLimit().longValue())
                .testDataRef(TestDataRef.builder().problemId(problem.getId()).testDataId(testData.getId())
                        .storagePath(testData.getStoragePath()).storageType(StorageType.LOCAL_FILE)
                        .testCaseCount(testData.getTestNodeCount()).version(testData.getVersion()).build())
                .build();
        judgeRequestConfigurer.configure(problem, request);
        // 直接绑定结果服务，push 时在该服务的事务中更新主表和关联数据。
        JudgeSession session = new JudgeSession(message,
                (task, outcome) -> submissionResultService.updateSubmission(submission, outcome));
        session.setRequest(request);
        return session;
    }
}
