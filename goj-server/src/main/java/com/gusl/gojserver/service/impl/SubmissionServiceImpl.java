package com.gusl.gojserver.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.common.common.BaseException;
import com.gusl.common.common.PageResult;
import com.gusl.common.constant.*;
import com.gusl.common.pojo.entity.JudgeTask;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.common.pojo.entity.ProblemTestData;
import com.gusl.common.utils.StringUtils;
import com.gusl.gojserver.config.properties.SysProperties;
import com.gusl.gojserver.mapper.*;
import com.gusl.gojserver.pojo.dto.Submission2JudgeDto;
import com.gusl.gojserver.pojo.dto.SubmissionSearchDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojserver.pojo.vo.SubmissionDetailVo;
import com.gusl.gojserver.pojo.vo.SubmissionListVo;
import com.gusl.gojserver.service.SubmissionService;
import com.gusl.gojserver.service.support.JudgeSourceValidator;
import com.gusl.gojserver.service.support.PageFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl extends ServiceImpl<SubmissionMapper, Submission> implements SubmissionService {

    private final SubmissionMapper submissionMapper;

    private final UserMapper userMapper;

    private final ProblemMapper problemMapper;

    private final ProblemTestDataMapper problemTestDataMapper;

    private final JudgeTaskMapper judgeTaskMapper;


    private final JudgeSourceValidator judgeSourceValidator;

    private final SysProperties sysProperties;

    private final PageFactory pageFactory;

    @Value("${goj.judge.task.max-attempts}")
    private Integer maxAttempts;


    /** 将用户的提交, 提交到测评机 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long submitProblemToJudge(Submission2JudgeDto submission2JudgeDto, LoginUser loginUser) {
        // 校验提交是否合法
        requirePublishedProblem(submission2JudgeDto.getProblemId());
        requirePublishedTestData(submission2JudgeDto.getProblemId());
        String sha256 = judgeSourceValidator.validateAndHash(
                submission2JudgeDto.getLanguage(),
                submission2JudgeDto.getSourceCode()
        );
        requireNoRecentDuplicate(submission2JudgeDto, loginUser.getUserId(), sha256);

        // 推送提交到测评机
        return enqueueSubmission(submission2JudgeDto, loginUser.getUserId(), sha256);
    }



    /** 保存提交并将 submissionId 放入 Judge 队列。 */
    private Long enqueueSubmission(Submission2JudgeDto submission2JudgeDto, Long userId, String sha256) {

        // 保存 submission 到数据库
        Submission submission = BeanUtil.copyProperties(submission2JudgeDto, Submission.class);
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

        // 保存 judgeTask 到数据库
        JudgeTask judgeTask = JudgeTask.builder()
                .businessId(submission.getId())
                .status(JudgeTaskStatus.PENDING)
                .taskType(JudgeTaskType.SUBMISSION)
                .taskVersion(1)
                .maxAttempts(maxAttempts)
                .build();
        judgeTaskMapper.insert(judgeTask);

        return submission.getId();
    }



    /** 查询提交详情 */
    @Override
    public SubmissionDetailVo getSubmissionById(Long submissionId) {
        Submission submission = getById(submissionId);
        if (submission == null) {
            throw new BaseException("提交不存在");
        }

        SubmissionDetailVo vo = new SubmissionDetailVo();
        BeanUtil.copyProperties(submission, vo);
        vo.setUsername(userMapper.selectById(submission.getUserId()).getUsername());
        return vo;
    }



    /** 获取我的提交 */
    @Override
    public PageResult<SubmissionListVo> getMySubmissionList(LoginUser loginUser, SubmissionSearchDto condition) {

        Page<Submission> page = pageFactory.create(condition);

        Page<Submission> submissions = submissionMapper.selectPage(
                page,
                Wrappers.<Submission>lambdaQuery()
                        .eq(Submission::getUserId, loginUser.getUserId())
                        .eq(condition.getProblemId() != null,
                                Submission::getProblemId,
                                condition.getProblemId())
                        .eq(StringUtils.isNotEmpty(condition.getLanguage()),
                                Submission::getLanguage,
                                condition.getLanguage())
                        .eq(StringUtils.isNotEmpty(condition.getStatus()),
                                Submission::getStatus,
                                condition.getStatus())
                        .orderByDesc(Submission::getId)
        );

        // 将实体分页数据转换为 VO 分页数据
        IPage<SubmissionListVo> voPage = submissions.convert(submission ->
                        BeanUtil.copyProperties(
                                submission,
                                SubmissionListVo.class
                        )
                );
        return PageResult.of(voPage);
    }

    /** 获取最近几次提交 */
    @Override
    public PageResult<SubmissionListVo> getMyRecentSubmission(LoginUser loginUser) {
        Page<Submission> submissionPage = submissionMapper.selectPage(
                new Page<>(1, sysProperties.getProfile().getRecentSubmissionSize(), false),
                Wrappers.<Submission>lambdaQuery()
                        .eq(Submission::getUserId, loginUser.getUserId())
                        .orderByDesc(Submission::getId)
        );
        IPage<SubmissionListVo> res = submissionPage.convert(s ->
                BeanUtil.copyProperties(s, SubmissionListVo.class)
        );
        return PageResult.of(res);
    }


    /** 拒绝重复提交相同代码的频率 */
    private void requireNoRecentDuplicate(Submission2JudgeDto dto, Long userId, String sha256) {
        Long count = submissionMapper.selectCount(
                Wrappers.<Submission>lambdaQuery()
                        .eq(Submission::getProblemId, dto.getProblemId())
                        .eq(Submission::getSourceSha256, sha256)
                        .eq(Submission::getUserId, userId)
                        .eq(Submission::getLanguage, dto.getLanguage())
                        .ge(Submission::getSubmissionTime,
                                LocalDateTime.now().minusMinutes(
                                        sysProperties.getProfile()
                                                .getFrequencyOfRepeatedSubmissions()
                                )
                        )
        );
        if(count > 0){
            throw new BaseException("重复提交相同代码频率过高");
        }
    }



    /** 校验普通提交对应的题目已经发布。*/
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



    /** 普通提交只能使用已发布并激活的正式测试数据。 */
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
