package com.gusl.gojserver.problem.application;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.common.common.BaseException;
import com.gusl.common.constant.*;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojserver.judgetask.infrastructure.persistence.JudgeTaskMapper;
import com.gusl.gojserver.problem.infrastructure.persistence.ProblemMapper;
import com.gusl.gojserver.problem.infrastructure.persistence.ProblemReviewSubmissionMapper;
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
 * 管理员验题提交服务实现，使用独立数据表和独立 Redis 队列。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemReviewJudgeServiceImpl
        extends ServiceImpl<ProblemReviewSubmissionMapper, ProblemReviewSubmission>
        implements ProblemReviewJudgeService {

    private final ProblemReviewSubmissionMapper reviewSubmissionMapper;

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

        // 固定本次任务使用的测试数据集，避免 Worker 再按题目状态猜测数据来源。
        ProblemTestData testData = requireTestData(problemId, dto.getVersion());

        // 执行普通提交与验题提交共同的语言、源码和摘要校验。
        String sha256 = judgeSourceValidator.validateAndHash(dto.getLanguage(), dto.getSourceCode());

        return enqueueSubmission(problemId, testData, loginUser.getUserId(), sha256, dto.getLanguage(), dto.getSourceCode());
    }

    /** 保存验题提交和可调度任务。 */
    private Long enqueueSubmission(
            Long problemId,
            ProblemTestData data,
            Long userId,
            String sha256,
            String language,
            String sourceCode
    ){
        // 写入独立验题表和任务表，由任务调度器投递 Redis 队列。
        ProblemReviewSubmission submission = ProblemReviewSubmission.builder()
                .problemId(problemId)
                .problemTestDataId(data.getId())
                .reviewerId(userId)
                .language(language)
                .sourceCode(sourceCode)
                .sourceSha256(sha256)
                .status(JudgingConstant.IN_QUEUE)
                .submissionTime(LocalDateTime.now())
                .build();
        reviewSubmissionMapper.insert(submission);


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
        ProblemReviewSubmission submission = reviewSubmissionMapper.selectById(reviewSubmissionId);
        if (submission == null) {
            throw new BaseException("验题提交不存在");
        }
        return BeanUtil.copyProperties(submission, ProblemReviewSubmissionVo.class);
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

    /** 获取管理员指定的数据版本，不要求启用，也不依赖题目状态。 */
    private ProblemTestData requireTestData(Long problemId, Integer version) {
        if (version == null || version <= 0) {
            throw new BaseException("请选择验题使用的测试数据版本");
        }
        ProblemTestData testData = problemTestDataMapper.selectOne(
                Wrappers.<ProblemTestData>lambdaQuery()
                        .eq(ProblemTestData::getProblemId, problemId)
                        .eq(ProblemTestData::getVersion, version)
        );
        if (testData == null) {
            throw new BaseException("测试数据版本不存在");
        }
        return testData;
    }
}
