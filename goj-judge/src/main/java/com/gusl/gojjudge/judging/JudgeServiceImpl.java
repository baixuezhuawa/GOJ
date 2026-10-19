package com.gusl.gojjudge.judging;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gusl.common.constant.*;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojjudge.judging.engine.*;
import com.gusl.gojjudge.judging.exception.JudgeSystemException;
import com.gusl.gojjudge.judging.mapper.*;
import com.gusl.gojjudge.judging.model.CheckerConfig;
import com.gusl.gojjudge.judging.model.TestDataRef;
import com.gusl.gojjudge.judging.result.SubmissionResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 测评准备服务，按统一提交记录加载材料并绑定结果写回。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeServiceImpl implements JudgeService {
    private final SubmissionMapper submissionMapper;
    private final SubmissionResultService submissionResultService;
    private final ProblemMapper problemMapper;
    private final ProblemTestDataMapper problemTestDataMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ProblemCheckerMapper problemCheckerMapper;
    private final JudgeEngine judgeEngine;

    /** 准备材料后执行评测，并要求成功写入终态。 */
    @Override
    public void judge(JudgeTaskMessage message) {
        // 从统一提交表加载代码和场景对应的测试数据。
        JudgeSession cur = prepare(message);
        if (cur == null) {
            return;
        }
        try {
            // 写入执行状态，评测结果仍由会话统一写回。
            cur.status(JudgingConstant.WAIT).push();
            judgeEngine.judge(cur);
            if (!cur.isFinished()) {
                throw new JudgeSystemException("判题引擎返回时尚未写入终态");
            }
        } catch (Exception exception) {
            cur.dead();
            throw exception;
        }
    }

    /** 所有场景都通过统一提交表判断终态。 */
    @Override
    public boolean isBusinessTerminal(String taskType, Long businessId) {
        Submission submission = submissionMapper.selectById(businessId);
        return submission != null && JudgingConstant.TERMINAL_STATUSES.contains(submission.getStatus());
    }

    /** 准备上下文 */
    private JudgeSession prepare(JudgeTaskMessage message) {
        Submission submission = submissionMapper.selectById(message.getBusinessId());
        if (submission == null) {
            throw new JudgeSystemException("提交记录不存在: " + message.getBusinessId());
        }
        if (JudgingConstant.TERMINAL_STATUSES.contains(submission.getStatus())) {
            return null;
        }
        Problem problem = problemMapper.selectById(submission.getProblemId());
        if (problem == null) {
            throw new JudgeSystemException("题目已不存在");
        }
        // 读取当前场景使用的数据，再配置普通或特殊判题参数。
        ProblemTestData testData = loadTestData(submission);

        JudgeExecutionRequest request = JudgeExecutionRequest.builder()
                .language(submission.getLanguage()).sourceCode(submission.getSourceCode())
                .timeLimitMs(problem.getTimeLimit().longValue()).memoryLimitKb(problem.getMemoryLimit().longValue())
                .testDataRef(TestDataRef.builder().problemId(problem.getId()).testDataId(testData.getId())
                        .storagePath(testData.getStoragePath()).storageType(StorageType.LOCAL_FILE)
                        .testCaseCount(testData.getTestNodeCount()).version(testData.getVersion()).build()).build();

        CheckerConfig config = new CheckerConfig();
        if (ProblemType.NORMAL.equals(problem.getProblemType())) {
            config.setCheckType(CheckType.TEXT);
        } else if (ProblemType.SPECIAL.equals(problem.getProblemType())) {
            ProblemChecker checker = problemCheckerMapper.selectOne(
                    Wrappers.<ProblemChecker>lambdaQuery().eq(ProblemChecker::getProblemId, problem.getId()));
            if (checker == null) {
                throw new JudgeSystemException("题目未配置校验程序: " + problem.getId());
            }
            config.setCheckType(CheckType.SPECIAL);
            config.setLanguage(checker.getLanguage());
            config.setSourceCode(checker.getSourceCode());
        } else if (ProblemType.INTERACTION.equals(problem.getProblemType())) {
            // 交互题由 Engine 统一给出未支持提示。
        }
        else {
            throw new JudgeSystemException("不支持的题目类型: " + problem.getProblemType());
        }
        request.setProblemType(problem.getProblemType());
        request.setCheckerConfig(config);


        JudgeSession session = new JudgeSession(
                message,
                (task, outcome) -> submissionResultService.updateSubmission(submission, outcome)
        );
        session.setRequest(request);

        return session;
    }

    /** 普通提交取启用数据，比赛取绑定数据，验题取最新上传版本。 */
    private ProblemTestData loadTestData(Submission submission) {

        // 比赛提交
        if (SubmissionType.CONTEST.equals(submission.getSubmissionType())) {
            ContestProblem binding = contestProblemMapper.selectOne(Wrappers.<ContestProblem>lambdaQuery()
                    .eq(ContestProblem::getContestId, submission.getContestId())
                    .eq(ContestProblem::getProblemId, submission.getProblemId())
                    .eq(ContestProblem::getReleaseStatus, ContestProblemStatus.OPENING));
            if (binding == null) {
                throw new JudgeSystemException("该比赛题目不存在或未启用");
            }
            ProblemTestData data = problemTestDataMapper.selectById(binding.getTestDataId());
            if (data == null) {
                throw new JudgeSystemException("比赛测试数据不存在");
            }
            return data;
        }


        LambdaQueryWrapper<ProblemTestData> wrapper = Wrappers.<ProblemTestData>lambdaQuery()
                .eq(ProblemTestData::getProblemId, submission.getProblemId());
        if (SubmissionType.PROBLEM_REVIEW.equals(submission.getSubmissionType())) {
            wrapper.orderByDesc(ProblemTestData::getVersion).last("LIMIT 1");
        } else {
            wrapper.eq(ProblemTestData::getActive, true);
        }


        ProblemTestData data = problemTestDataMapper.selectOne(wrapper);
        if (data == null) {
            throw new JudgeSystemException("题目没有可用的测试数据");
        }
        return data;
    }
}
