package com.gusl.gojserver.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.common.common.BaseException;
import com.gusl.common.constant.JudgeQueueConstant;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.constant.ProblemStatus;
import com.gusl.common.constant.ProblemTestDataStatus;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.common.pojo.entity.ProblemTestData;
import com.gusl.gojserver.mapper.ProblemMapper;
import com.gusl.gojserver.mapper.ProblemTestDataMapper;
import com.gusl.gojserver.mapper.SubmissionMapper;
import com.gusl.gojserver.mapper.UserMapper;
import com.gusl.gojserver.pojo.dto.SubmissionDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojserver.pojo.vo.SubmissionVo;
import com.gusl.gojserver.service.SubmissionService;
import com.gusl.gojserver.service.support.JudgeSourceValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl extends ServiceImpl<SubmissionMapper, Submission> implements SubmissionService {

    private final SubmissionMapper submissionMapper;

    private final StringRedisTemplate redisTemplate;

    private final UserMapper userMapper;

    private final ProblemMapper problemMapper;

    private final ProblemTestDataMapper problemTestDataMapper;

    private final JudgeSourceValidator judgeSourceValidator;

    /**
     * 将用户的提交, 提交到测评机
     * @param submissionDto 提交信息
     */
    @Override
    public Long submitProblemToJudge(SubmissionDto submissionDto, LoginUser loginUser) {
        requirePublishedProblem(submissionDto.getProblemId());
        requirePublishedTestData(submissionDto.getProblemId());
        String sha256 = judgeSourceValidator.validateAndHash(
                submissionDto.getLanguage(),
                submissionDto.getSourceCode()
        );
        requireNoRecentDuplicate(submissionDto, loginUser.getUserId(), sha256);
        Submission submission = BeanUtil.copyProperties(submissionDto, Submission.class);
        return enqueueSubmission(submission, loginUser.getUserId(), sha256);
    }

    /**
     * 保存提交并将 submissionId 放入 Judge 队列。
     */
    private Long enqueueSubmission(Submission submission, Long userId, String sha256) {
        submission.setStatus(JudgingConstant.IN_QUEUE);
        submission.setUserId(userId);
        submission.setSubmissionTime(LocalDateTime.now());
        submission.setSourceSha256(sha256);
        submissionMapper.insert(submission);
        log.info("提交用户id:{} 测评问题id:{} 测评id:{} ----- 开始测评",
                submission.getUserId(),
                submission.getProblemId(),
                submission.getId()
        );
        // 构造测评请求... v1 版本暂时跳过
        // 通过redis队列充当消息队列发送请求.
        redisTemplate.opsForList().leftPush(
                JudgeQueueConstant.SUBMISSION_READY_QUEUE,
                submission.getId().toString()
        );
        log.info("submissionId:{} in queue", submission.getId());
        return submission.getId();
    }

    @Override
    public SubmissionVo getSubmissionById(Long submissionId) {
        SubmissionVo vo = new SubmissionVo();
        Submission submission = getById(submissionId);
        BeanUtil.copyProperties(submission, vo);
        vo.setUsername(userMapper.selectById(submission.getUserId()).getUsername());
        return vo;
    }

    /**
     * 拒绝用户在两分钟内重复提交相同代码。
     */
    private void requireNoRecentDuplicate(SubmissionDto dto, Long userId, String sha256) {
        Long count = submissionMapper.selectCount(
                Wrappers.<Submission>lambdaQuery()
                        .eq(Submission::getProblemId, dto.getProblemId())
                        .eq(Submission::getSourceSha256, sha256)
                        .eq(Submission::getUserId, userId)
                        .eq(Submission::getLanguage, dto.getLanguage())
                        .ge(Submission::getSubmissionTime, LocalDateTime.now().minusMinutes(2))
        );
        if(count > 0){
            throw new BaseException("近两分钟内有重复提交");
        }
    }

    /**
     * 校验普通提交对应的题目已经发布。
     */
    private void requirePublishedProblem(Long problemId) {
        if (problemId == null) {
            throw new BaseException("题目 id 不能为空");
        }
        Long count = problemMapper.selectCount(
                Wrappers.<Problem>lambdaQuery()
                        .eq(Problem::getId, problemId)
                        .eq(Problem::getStatus, ProblemStatus.PUBLISH)
        );
        if (count != 1) {
            throw new BaseException(
                    "题目不存在或尚未发布"
            );
        }
    }

    /**
     * 普通提交只能使用已发布并激活的正式测试数据。
     */
    private void requirePublishedTestData(Long problemId) {
        Long count = problemTestDataMapper.selectCount(
                Wrappers.<ProblemTestData>lambdaQuery()
                        .eq(ProblemTestData::getProblemId, problemId)
                        .eq(ProblemTestData::getStatus, ProblemTestDataStatus.READY)
                        .eq(ProblemTestData::getActive, true)
        );
        if (count != 1) {
            throw new BaseException("题目没有可用的正式测试数据");
        }
    }
}
