package com.gusl.gojjudge.judging.application.handler;

import com.gusl.gojjudge.judging.application.result.SubmissionResultService;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.constant.*;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojjudge.judging.domain.exception.JudgeSystemException;
import com.gusl.gojjudge.judging.infrastructure.persistence.ProblemMapper;
import com.gusl.gojjudge.judging.infrastructure.persistence.ProblemTestDataMapper;
import com.gusl.gojjudge.judging.infrastructure.persistence.SubmissionMapper;
import com.gusl.gojjudge.judging.application.engine.JudgeRequestConfigurer;
import com.gusl.gojjudge.judging.application.engine.JudgeExecutionRequest;
import com.gusl.gojjudge.judging.domain.model.TestDataRef;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 普通提交处理器
 */
@Component
@RequiredArgsConstructor
public class NormalSubmissionJudgeHandler implements SubmissionJudgeHandler {

    private final SubmissionMapper submissionMapper;

    private final ProblemMapper problemMapper;

    private final ProblemTestDataMapper problemTestDataMapper;

    private final SubmissionResultService submissionResultService;

    private final JudgeRequestConfigurer judgeRequestConfigurer;

    @Value("${goj.judge.data-root}")
    private String dataRoot;

    @Override
    public String handlerTaskType() {
        return JudgeTaskType.SUBMISSION;
    }

    @Override
    public SubmissionJudgeContext prepare(JudgeTaskMessage message) {
        JudgeExecutionRequest request = new JudgeExecutionRequest();

        Submission submission = submissionMapper.selectById(message.getBusinessId());
        if (submission == null) {
            throw new JudgeSystemException("普通提交不存在: " + message.getBusinessId());
        }
        if (JudgingConstant.TERMINAL_STATUSES.contains(submission.getStatus())) {
            return new LoadedSubmissionJudgeContext(null, (task, outcome) -> false, true);
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

        return new LoadedSubmissionJudgeContext(
                request,
                (task, outcome) -> {
                    return submissionResultService.updateSubmission(submission, outcome);
                },
                false
        );
    }

}
