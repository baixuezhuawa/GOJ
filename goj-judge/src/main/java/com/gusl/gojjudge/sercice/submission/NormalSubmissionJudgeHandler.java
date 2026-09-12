package com.gusl.gojjudge.sercice.submission;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.constant.*;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojjudge.exception.JudgeSystemException;
import com.gusl.gojjudge.mapper.ProblemMapper;
import com.gusl.gojjudge.mapper.ProblemTestDataMapper;
import com.gusl.gojjudge.mapper.SubmissionMapper;
import com.gusl.gojjudge.pojo.entity.CheckerConfig;
import com.gusl.gojjudge.pojo.entity.JudgeExecutionRequest;
import com.gusl.gojjudge.pojo.entity.TestDataRef;
import com.gusl.gojjudge.pojo.entity.LoadedSubmissionJudgeContext;
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

        request.setLanguage(submission.getLanguage());
        request.setSourceCode(submission.getSourceCode());
        request.setTimeLimitMs(problem.getTimeLimit().longValue());
        request.setMemoryLimitKb(problem.getMemoryLimit().longValue());

        CheckerConfig checkerConfig = new CheckerConfig();
        checkerConfig.setCheckType(CheckType.TEXT);
        request.setCheckerConfig(checkerConfig);

        request.setExecutionMode(ExecutionMode.BATCH_TEST_NODE);

        ProblemTestData testData = problemTestDataMapper.selectOne(
                Wrappers.<ProblemTestData>lambdaQuery()
                        .eq(ProblemTestData::getProblemId, problem.getId())
                        .eq(ProblemTestData::getActive, 1)
                        .eq(ProblemTestData::getStatus, ProblemTestDataStatus.READY)
        );
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
