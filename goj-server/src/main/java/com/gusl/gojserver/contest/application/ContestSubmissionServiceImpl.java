package com.gusl.gojserver.contest.application;


import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.common.common.BaseException;
import com.gusl.common.common.PageQuery;
import com.gusl.common.common.PageResult;
import com.gusl.common.constant.*;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojserver.shared.infrastructure.config.properties.SysProperties;
import com.gusl.gojserver.contest.infrastructure.persistence.ContestMapper;
import com.gusl.gojserver.contest.infrastructure.persistence.ContestParticipateMapper;
import com.gusl.gojserver.contest.infrastructure.persistence.ContestProblemMapper;
import com.gusl.gojserver.submission.infrastructure.persistence.SubmissionMapper;
import com.gusl.gojserver.judgetask.infrastructure.persistence.JudgeTaskMapper;
import com.gusl.gojserver.contest.application.dto.ContestSubmission2JudgeDto;
import com.gusl.gojserver.submission.application.dto.Submission2JudgeDto;
import com.gusl.gojserver.account.infrastructure.security.LoginUser;
import com.gusl.gojserver.contest.application.vo.ContestSubmissionDetailVo;
import com.gusl.gojserver.contest.application.vo.ContestSubmissionListVo;
import com.gusl.gojserver.submission.application.vo.SubmissionVo;
import com.gusl.gojserver.submission.application.SubmissionService;
import com.gusl.gojserver.judgetask.application.support.JudgeSourceValidator;
import com.gusl.gojserver.shared.application.pagination.PageFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContestSubmissionServiceImpl extends ServiceImpl<SubmissionMapper, Submission> implements ContestSubmissionService {


    private final SubmissionMapper submissionMapper;

    private final ContestProblemMapper contestProblemMapper;

    private final JudgeTaskMapper judgeTaskMapper;

    private final ContestParticipateMapper contestParticipateMapper;

    private final SubmissionService submissionService;


    private final SysProperties sysProperties;

    private final JudgeSourceValidator judgeSourceValidator;

    private final PageFactory pageFactory;
    private final ContestMapper contestMapper;

    @Value("${goj.judge.task.max-attempts}")
    private Integer maxAttempts;



    /**
     * 比赛问题提交
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public SubmissionVo submitContestProblemToJudge(
            Long contestId,
            String problemCode,
            ContestSubmission2JudgeDto dto,
            LoginUser loginUser
    ) {
        // 判断用户是否报名
        Long isParticipate = contestParticipateMapper.selectCount(
                Wrappers.<ContestParticipate>lambdaQuery()
                        .eq(ContestParticipate::getContestId, contestId)
                        .eq(ContestParticipate::getUserId, loginUser.getUserId())
        );

        if (isParticipate != 1){
            throw new BaseException("未报名不允许提交");
        }

        // 判断问题是否存在
        ContestProblem contestProblem = contestProblemMapper.selectOne(
                Wrappers.<ContestProblem>lambdaQuery()
                        .eq(ContestProblem::getContestId, contestId)
                        .eq(ContestProblem::getDisplayCode, problemCode)
                        .eq(ContestProblem::getReleaseStatus, ContestProblemStatus.OPENING)
        );

        // 如果为 null 说明比赛结束或者题目已经公开, 那就走正常提交逻辑
        if (contestProblem == null){
            log.info("题目不存在或者非比赛状态");
            return tryToRegularSubmission(contestId, problemCode, dto, loginUser);
        }

        // 如果不为null, 说明比赛还未结束, 那就还是比赛提交
        String sha256 = judgeSourceValidator.validateAndHash(
                dto.getLanguage(),
                dto.getSourceCode()
        );

        requireNoRecentDuplicate(
                contestId,
                contestProblem.getProblemId(),
                dto,
                loginUser.getUserId(),
                sha256
        );

        return new SubmissionVo(
                SubmissionType.CONTEST,
                enqueueContestSubmission(
                        contestId,
                        contestProblem.getProblemId(),
                        loginUser.getUserId(),
                        dto,
                        sha256
                )
        );
    }



    /**
     * 获取我的比赛提交列表
     */
    @Override
    public PageResult<ContestSubmissionListVo> getMyContestSubmissionList(
            Long contestId,
            PageQuery pageQuery,
            LoginUser loginUser
    ) {
        Page<Submission> page = pageFactory.create(pageQuery);

        Page<Submission> contestSubmissionPage = submissionMapper.selectPage(
                page,
                Wrappers.<Submission>lambdaQuery()
                        .eq(Submission::getUserId, loginUser.getUserId())
                        .eq(Submission::getContestId, contestId)
                        .eq(Submission::getSubmissionType, SubmissionType.CONTEST)
                        .orderByDesc(Submission::getId)
        );

        IPage<ContestSubmissionListVo> result = contestSubmissionPage.convert(contestSubmission -> {
            ContestSubmissionListVo vo =
                    BeanUtil.copyProperties(contestSubmission, ContestSubmissionListVo.class);
            ContestProblem contestProblem = contestProblemMapper.selectOne(
                    Wrappers.<ContestProblem>lambdaQuery()
                            .eq(ContestProblem::getContestId, contestId)
                            .eq(ContestProblem::getProblemId, contestSubmission.getProblemId())
            );
            vo.setDisplayName(contestProblem.getDisplayName());
            vo.setDisplayCode(contestProblem.getDisplayCode());
            return vo;
        });

        return PageResult.of(result);
    }



    /**
     * 获取比赛提交详细信息
     */
    @Override
    public ContestSubmissionDetailVo getContestSubmissionDetail(
            Long contestId,
            Long submissionId,
            LoginUser loginUser
    ) {
        Submission submission = submissionMapper.selectOne(
                Wrappers.<Submission> lambdaQuery()
                        .eq(Submission::getContestId, contestId)
                        .eq(Submission::getId, submissionId)
                        .eq(Submission::getSubmissionType, SubmissionType.CONTEST)
        );
        if(submission == null){
            throw new BaseException("该提交不存在");
        }

        Contest contest = contestMapper.selectOne(
                Wrappers.<Contest> lambdaQuery()
                        .eq(Contest::getId, contestId)
                        .notIn(Contest::getStatus, ContestStatus.DRAFT, ContestStatus.SCHEDULED)
        );
        if (contest == null){
            throw new BaseException("该比赛不存在/未开开始");
        }

        ContestSubmissionDetailVo vo =
                BeanUtil.copyProperties(submission, ContestSubmissionDetailVo.class);
        vo.setContestId(contestId);
        vo.setContestTitle(contest.getTitle());
        if (
                // 比赛期间, 不允许获取查看其他人的代码
                (
                    ContestStatus.RUNNING.equals(contest.getStatus()) ||
                    contest.getEndTime().isBefore(LocalDateTime.now())
                ) &&
                // 不是自己的提交
                !submission.getUserId().equals(loginUser.getUserId())

        ){
            vo.setSourceCode("hhh, 不允许偷看嗷");
        }
        return vo;
    }


    /**
     * 尝试提交到普通测评
     */
    private SubmissionVo tryToRegularSubmission(
            Long contestId,
            String problemCode,
            ContestSubmission2JudgeDto dto,
            LoginUser loginUser
    ){
        ContestProblem contestProblem = contestProblemMapper.selectOne(
                Wrappers.<ContestProblem>lambdaQuery()
                        .eq(ContestProblem::getContestId, contestId)
                        .eq(ContestProblem::getDisplayCode, problemCode)
                        .eq(ContestProblem::getReleaseStatus, ContestProblemStatus.PUBLISH)
        );
        if(contestProblem == null){
            throw new BaseException("当前阶段不允许提交该比赛题目");
        }

        return submissionService.submitProblemToJudge(
                new Submission2JudgeDto(
                        contestProblem.getProblemId(),
                        dto.getLanguage(),
                        dto.getSourceCode(),
                        contestId
                ),
                loginUser
        );
    }



    private Long enqueueContestSubmission(
            Long contestId,
            Long problemId,
            Long userId,
            ContestSubmission2JudgeDto dto,
            String sha256
    ){
        // 保存 submission 到数据库
        Submission submission = new Submission();
        submission.setStatus(JudgingConstant.IN_QUEUE);
        submission.setSubmissionType(SubmissionType.CONTEST);
        submission.setUserId(userId);
        submission.setContestId(contestId);
        submission.setProblemId(problemId);
        submission.setSubmissionTime(LocalDateTime.now());
        submission.setSourceSha256(sha256);
        submission.setSourceCode(dto.getSourceCode());
        submission.setLanguage(dto.getLanguage());
        submission.setVersion(1);
        submissionMapper.insert(submission);

        log.info("比赛id:{} 提交用户id:{} 测评问题id:{} 测评id:{}",
                contestId,
                userId,
                problemId,
                submission.getId()
        );

        // 保存 judgeTask 到数据库
        JudgeTask judgeTask = JudgeTask.builder()
                .businessId(submission.getId())
                .status(JudgeTaskStatus.PENDING)
                .taskType(JudgeTaskType.CONTEST_SUBMISSION)
                .taskVersion(1)
                .maxAttempts(maxAttempts)
                .build();
        judgeTaskMapper.insert(judgeTask);

        return submission.getId();
    }



    private void requireNoRecentDuplicate(
            Long contestId,
            Long problemId,
            ContestSubmission2JudgeDto dto,
            Long userId,
            String sha256
    ) {
        Long count = submissionMapper.selectCount(
                Wrappers.<Submission>lambdaQuery()
                        .eq(Submission::getContestId, contestId)
                        .eq(Submission::getProblemId, problemId)
                        .eq(Submission::getSubmissionType, SubmissionType.CONTEST)
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
        if (count > 0) {
            throw new BaseException("重复提交相同代码频率过高");
        }
    }
}
