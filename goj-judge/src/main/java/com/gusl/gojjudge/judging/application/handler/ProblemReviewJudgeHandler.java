package com.gusl.gojjudge.judging.application.handler;

import com.gusl.gojjudge.judging.application.result.SubmissionResultService;

import com.gusl.common.constant.JudgeTaskType;
import com.gusl.common.pojo.entity.JudgeTaskMessage;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.common.pojo.entity.ProblemReviewSubmission;
import com.gusl.common.pojo.entity.ProblemTestData;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.constant.*;
import com.gusl.gojjudge.judging.domain.exception.JudgeSystemException;
import com.gusl.gojjudge.judging.infrastructure.persistence.ProblemMapper;
import com.gusl.gojjudge.judging.infrastructure.persistence.ProblemReviewSubmissionMapper;
import com.gusl.gojjudge.judging.infrastructure.persistence.ProblemTestDataMapper;
import com.gusl.gojjudge.judging.application.engine.JudgeRequestConfigurer;
import com.gusl.gojjudge.judging.application.engine.JudgeExecutionRequest;
import com.gusl.gojjudge.judging.domain.model.TestDataRef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 验题提交处理器
 */
@Component
@RequiredArgsConstructor
public class ProblemReviewJudgeHandler implements SubmissionJudgeHandler {

    private final ProblemReviewSubmissionMapper submissionMapper;
    private final ProblemMapper problemMapper;
    private final ProblemTestDataMapper problemTestDataMapper;
    private final SubmissionResultService submissionResultService;
    private final JudgeRequestConfigurer judgeRequestConfigurer;


    @Override
    public String handlerTaskType() {
        return JudgeTaskType.PROBLEM_REVIEW;
    }

    @Override
    public SubmissionJudgeContext prepare(JudgeTaskMessage message) {
        ProblemReviewSubmission submission = submissionMapper.selectById(message.getBusinessId());
        if (submission == null) {
            throw new JudgeSystemException("验题提交不存在: " + message.getBusinessId());
        }
        if (JudgingConstant.TERMINAL_STATUSES.contains(submission.getStatus())) {
            return new LoadedSubmissionJudgeContext(null, (task, outcome) -> false, true);
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
        return new LoadedSubmissionJudgeContext(request,
                (task, outcome) -> submissionResultService.updateSubmission(submission, outcome), false);
    }
}
