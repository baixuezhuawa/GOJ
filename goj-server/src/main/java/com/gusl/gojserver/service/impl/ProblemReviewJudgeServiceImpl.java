package com.gusl.gojserver.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.common.common.BaseException;
import com.gusl.common.constant.*;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojserver.mapper.JudgeTaskMapper;
import com.gusl.gojserver.mapper.ProblemMapper;
import com.gusl.gojserver.mapper.ProblemReviewSubmissionMapper;
import com.gusl.gojserver.mapper.ProblemTestDataMapper;
import com.gusl.gojserver.pojo.dto.ProblemReviewJudgeDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.vo.ProblemReviewSubmissionVo;
import com.gusl.gojserver.service.ProblemReviewJudgeService;
import com.gusl.gojserver.service.support.JudgeSourceValidator;
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

        // 管理员只能为仍处于待审核状态的题目创建验题任务。
        requirePendingProblem(problemId);

        // 固定本次任务使用的测试数据集，避免 Worker 再按题目状态猜测数据来源。
        ProblemTestData testData = requirePendingTestData(problemId);

        // 执行普通提交与验题提交共同的语言、源码和摘要校验。
        String sha256 = judgeSourceValidator.validateAndHash(dto.getLanguage(), dto.getSourceCode());
        requireNoRecentDuplicate(problemId, loginUser.getUserId(), dto.getLanguage(), sha256);

        return enqueueSubmission(problemId, testData, loginUser.getUserId(), sha256, dto.getLanguage(), dto.getSourceCode());
    }

    private Long enqueueSubmission(
            Long problemId,
            ProblemTestData data,
            Long userId,
            String sha256,
            String language,
            String sourceCode
    ){
        // 写入独立验题表，并把验题记录 id 放入独立 Redis 队列。
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
     * 校验题目仍处于待审核状态。
     */
    private void requirePendingProblem(Long problemId) {
        if (problemId == null) {
            throw new BaseException("题目 id 不能为空");
        }
        Long count = problemMapper.selectCount(
                Wrappers.<Problem>lambdaQuery()
                        .eq(Problem::getId, problemId)
                        .eq(Problem::getStatus, ProblemStatus.PENDING)
        );
        if (count != 1) {
            throw new BaseException("待审核题目不存在或已被处理");
        }
    }

    /**
     * 获取最近一次完成结构校验的待审核测试数据。
     */
    private ProblemTestData requirePendingTestData(Long problemId) {
        ProblemTestData testData = problemTestDataMapper.selectOne(
                Wrappers.<ProblemTestData>lambdaQuery()
                        .eq(ProblemTestData::getProblemId, problemId)
                        .eq(ProblemTestData::getActive, false)
                        .eq(ProblemTestData::getStatus, ProblemTestDataStatus.EXTRACTED)
                        .orderByDesc(ProblemTestData::getId)
                        .last("LIMIT 1")
        );
        if (testData == null) {
            throw new BaseException("题目没有可供验题的测试数据");
        }
        return testData;
    }

    /**
     * 拒绝管理员在两分钟内重复提交相同验题代码。
     */
    private void requireNoRecentDuplicate(Long problemId, Long reviewerId, String language, String sha256) {
        Long count = reviewSubmissionMapper.selectCount(
                Wrappers.<ProblemReviewSubmission>lambdaQuery()
                        .eq(ProblemReviewSubmission::getProblemId, problemId)
                        .eq(ProblemReviewSubmission::getReviewerId, reviewerId)
                        .eq(ProblemReviewSubmission::getLanguage, language)
                        .eq(ProblemReviewSubmission::getSourceSha256, sha256)
                        .ge(ProblemReviewSubmission::getSubmissionTime, LocalDateTime.now().minusMinutes(2))
        );
        if (count > 0) {
            throw new BaseException("近两分钟内有重复验题提交");
        }
    }
}
