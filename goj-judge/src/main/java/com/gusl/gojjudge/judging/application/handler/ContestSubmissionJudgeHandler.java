package com.gusl.gojjudge.judging.application.handler;

import com.gusl.gojjudge.judging.application.result.SubmissionResultService;

import com.gusl.common.constant.JudgeTaskType;
import com.gusl.common.pojo.entity.JudgeTaskMessage;
import com.gusl.common.pojo.entity.ContestProblem;
import com.gusl.common.pojo.entity.ContestSubmission;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.common.pojo.entity.ProblemTestData;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.constant.*;
import com.gusl.gojjudge.judging.domain.exception.JudgeSystemException;
import com.gusl.gojjudge.judging.infrastructure.persistence.ContestProblemMapper;
import com.gusl.gojjudge.judging.infrastructure.persistence.ContestSubmissionMapper;
import com.gusl.gojjudge.judging.infrastructure.persistence.ProblemMapper;
import com.gusl.gojjudge.judging.infrastructure.persistence.ProblemTestDataMapper;
import com.gusl.gojjudge.judging.application.engine.JudgeRequestConfigurer;
import com.gusl.gojjudge.judging.application.engine.JudgeExecutionRequest;
import com.gusl.gojjudge.judging.domain.model.TestDataRef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 比赛提交处理器
 */
@Component
@RequiredArgsConstructor
public class ContestSubmissionJudgeHandler implements SubmissionJudgeHandler {

    private final ContestSubmissionMapper submissionMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ProblemMapper problemMapper;
    private final ProblemTestDataMapper problemTestDataMapper;
    private final SubmissionResultService submissionResultService;
    private final JudgeRequestConfigurer judgeRequestConfigurer;


    @Override
    public String handlerTaskType() {
        return JudgeTaskType.CONTEST_SUBMISSION;
    }

    @Override
    public SubmissionJudgeContext prepare(JudgeTaskMessage message) {
        ContestSubmission submission = submissionMapper.selectById(message.getBusinessId());
        if (submission == null) {
            throw new JudgeSystemException("比赛提交不存在: " + message.getBusinessId());
        }
        if (JudgingConstant.TERMINAL_STATUSES.contains(submission.getStatus())) {
            return new LoadedSubmissionJudgeContext(null, (task, outcome) -> false, true);
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
        return new LoadedSubmissionJudgeContext(request,
                (task, outcome) -> submissionResultService.updateSubmission(submission, outcome), false);
    }
}
