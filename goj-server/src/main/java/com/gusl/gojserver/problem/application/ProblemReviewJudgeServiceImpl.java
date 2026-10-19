package com.gusl.gojserver.problem.application;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.common.common.BaseException;
import com.gusl.common.constant.*;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojserver.judgetask.infrastructure.persistence.JudgeTaskMapper;
import com.gusl.gojserver.problem.infrastructure.persistence.ProblemMapper;
import com.gusl.gojserver.submission.infrastructure.persistence.SubmissionMapper;
import com.gusl.gojserver.problem.infrastructure.persistence.ProblemTestDataMapper;
import com.gusl.gojserver.problem.application.dto.ProblemReviewJudgeDto;
import com.gusl.gojserver.account.infrastructure.security.LoginUser;
import com.gusl.gojserver.problem.application.vo.ProblemReviewSubmissionVo;
import com.gusl.gojserver.judgetask.application.support.JudgeSourceValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 管理员验题提交服务，保存到统一提交表并创建调度任务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemReviewJudgeServiceImpl
        extends ServiceImpl<SubmissionMapper, Submission>
        implements ProblemReviewJudgeService {

    private final SubmissionMapper submissionMapper;

    private final ProblemMapper problemMapper;

    private final ProblemTestDataMapper problemTestDataMapper;

    private final JudgeTaskMapper judgeTaskMapper;

    private final JudgeSourceValidator judgeSourceValidator;

    /**
     * 创建管理员验题任务。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long submit(Long problemId, ProblemReviewJudgeDto dto, LoginUser loginUser) {
        if (dto == null) {
            throw new BaseException("验题请求不能为空");
        }

        // 管理员可对任意状态的题目验题。
        requireProblem(problemId);

        // 提交时检查数据存在，Worker 执行时读取最新上传版本。
        requireLatestTestData(problemId);

        // 执行普通提交与验题提交共同的语言、源码和摘要校验。
        String sha256 = judgeSourceValidator.validateAndHash(dto.getLanguage(), dto.getSourceCode());

        return enqueueSubmission(problemId, loginUser.getUserId(), sha256, dto.getLanguage(), dto.getSourceCode());
    }

    /** 保存验题提交和可调度任务。 */
    private Long enqueueSubmission(
            Long problemId,
            Long userId,
            String sha256,
            String language,
            String sourceCode
    ){
        // 在同一事务内保存提交记录和调度任务。
        Submission submission = new Submission();
        submission.setProblemId(problemId);
        submission.setUserId(userId);
        submission.setSubmissionType(SubmissionType.PROBLEM_REVIEW);
        submission.setVersion(1);
        submission.setLanguage(language);
        submission.setSourceCode(sourceCode);
        submission.setSourceSha256(sha256);
        submission.setStatus(JudgingConstant.IN_QUEUE);
        submission.setSubmissionTime(LocalDateTime.now());
        submissionMapper.insert(submission);


        JudgeTask judgeTask = JudgeTask.builder()
                .businessId(submission.getId())
                .status(JudgeTaskStatus.PENDING)
                .taskType(JudgeTaskType.PROBLEM_REVIEW)
                .taskVersion(1)
                .build();
        judgeTaskMapper.insert(judgeTask);

        return submission.getId();
    }

    /**
     * 获取管理员验题任务状态。
     */
    @Override
    public ProblemReviewSubmissionVo getReviewSubmission(Long reviewSubmissionId) {
        Submission submission = submissionMapper.selectById(reviewSubmissionId);
        if (submission == null || !SubmissionType.PROBLEM_REVIEW.equals(submission.getSubmissionType())) {
            throw new BaseException("验题提交不存在");
        }
        ProblemReviewSubmissionVo vo = BeanUtil.copyProperties(submission, ProblemReviewSubmissionVo.class);
        vo.setReviewerId(submission.getUserId());
        return vo;
    }

    /**
     * 校验题目存在。
     */
    private void requireProblem(Long problemId) {
        if (problemId == null) {
            throw new BaseException("题目 id 不能为空");
        }
        Long count = problemMapper.selectCount(
                Wrappers.<Problem>lambdaQuery()
                        .eq(Problem::getId, problemId)
        );
        if (count != 1) {
            throw new BaseException("题目不存在");
        }
    }

    /** 检查题目最新上传版本存在，不要求启用。 */
    private ProblemTestData requireLatestTestData(Long problemId) {
        ProblemTestData testData = problemTestDataMapper.selectOne(
                Wrappers.<ProblemTestData>lambdaQuery()
                        .eq(ProblemTestData::getProblemId, problemId)
                        .orderByDesc(ProblemTestData::getVersion)
                        .last("LIMIT 1")
        );
        if (testData == null) {
            throw new BaseException("题目没有测试数据");
        }
        return testData;
    }
}
