package com.gusl.gojjudge.sercice.submission;

import com.gusl.common.constant.JudgeTaskType;
import com.gusl.common.pojo.entity.JudgeTaskMessage;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.common.pojo.entity.ProblemReviewSubmission;
import com.gusl.common.pojo.entity.ProblemTestData;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.constant.*;
import com.gusl.gojjudge.exception.JudgeSystemException;
import com.gusl.gojjudge.mapper.ProblemMapper;
import com.gusl.gojjudge.mapper.ProblemReviewSubmissionMapper;
import com.gusl.gojjudge.mapper.ProblemTestDataMapper;
import com.gusl.gojjudge.pojo.entity.*;
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
        Problem problem = problemMapper.selectOne(Wrappers.<Problem>lambdaQuery()
                .eq(Problem::getId, submission.getProblemId()).eq(Problem::getStatus, ProblemStatus.PENDING));
        ProblemTestData testData = problemTestDataMapper.selectOne(Wrappers.<ProblemTestData>lambdaQuery()
                .eq(ProblemTestData::getId, submission.getProblemTestDataId())
                .eq(ProblemTestData::getProblemId, submission.getProblemId())
                .eq(ProblemTestData::getActive, false)
                .eq(ProblemTestData::getStatus, ProblemTestDataStatus.EXTRACTED));
        if (problem == null || testData == null) {
            throw new JudgeSystemException("无法加载待审核题目或测试数据");
        }
        CheckerConfig checkerConfig = new CheckerConfig();
        checkerConfig.setCheckType(CheckType.TEXT);
        JudgeExecutionRequest request = JudgeExecutionRequest.builder()
                .language(submission.getLanguage()).sourceCode(submission.getSourceCode())
                .timeLimitMs(problem.getTimeLimit().longValue()).memoryLimitKb(problem.getMemoryLimit().longValue())
                .executionMode(ExecutionMode.BATCH_TEST_NODE)
                .checkerConfig(checkerConfig)
                .testDataRef(TestDataRef.builder().problemId(problem.getId()).testDataId(testData.getId())
                        .storagePath(testData.getStoragePath()).storageType(StorageType.LOCAL_FILE)
                        .testCaseCount(testData.getTestNodeCount()).version(testData.getVersion()).build())
                .build();
        return new LoadedSubmissionJudgeContext(request,
                (task, outcome) -> submissionResultService.updateSubmission(submission, outcome), false);
    }
}
