package com.gusl.gojserver.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.common.common.BaseException;
import com.gusl.common.common.PageQuery;
import com.gusl.common.common.PageResult;
import com.gusl.common.constant.ContestProblemStatus;
import com.gusl.common.constant.ContestStatus;
import com.gusl.common.constant.ProblemTestDataStatus;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojserver.mapper.*;
import com.gusl.gojserver.pojo.dto.AddProblem2ContestDto;
import com.gusl.gojserver.pojo.dto.ContestDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.vo.ContestDetailVo;
import com.gusl.gojserver.pojo.vo.ContestListVo;
import com.gusl.gojserver.pojo.vo.ContestProblemDetailVo;
import com.gusl.gojserver.pojo.vo.ContestProblemListVo;
import com.gusl.gojserver.service.ContestService;
import com.gusl.gojserver.service.support.PageFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContestServiceImpl extends ServiceImpl<ContestMapper, Contest> implements ContestService {

    private final ContestMapper contestMapper;

    private final ContestProblemMapper contestProblemMapper;

    private final ProblemMapper problemMapper;

    private final ProblemTestDataMapper testDataMapper;

    private final PageFactory pageFactory;

    private final ContestParticipateMapper participateMapper;



    /** 删除比赛题目 */
    @Override
    public void deleteContestProblem(Long contestId, Long problemId) {
        int delete = contestProblemMapper.delete(
                Wrappers.<ContestProblem>lambdaQuery()
                        .eq(ContestProblem::getContestId, contestId)
                        .eq(ContestProblem::getProblemId, problemId)
        );
        if (delete != 1){
            log.info("contest:{} -> problemId:{} 删除失败", contestId, problemId);
        }
        log.info("contest:{} -> problemId:{} 删除成功", contestId, problemId);
    }

    /** 删除比赛 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteContest(Long contestId) {
        contestMapper.deleteById(contestId);
        contestProblemMapper.delete(
                Wrappers.<ContestProblem> lambdaQuery()
                        .eq(ContestProblem::getContestId, contestId)
        );
    }

    /**
     * 推送比赛上线
     */
    @Override
    public void scheduleContest(Long contestId) {
        // 保证为草稿阶段, 开始注册时间一定是晚于现在推送时间的.
        int effectRows = contestMapper.update(
                Wrappers.<Contest> lambdaUpdate()
                        .set(Contest::getStatus, ContestStatus.SCHEDULED)
                        .eq(Contest::getId, contestId)
                        .eq(Contest::getStatus, ContestStatus.DRAFT)
                        .ge(Contest::getRegisterStartTime, LocalDateTime.now())
        );
        if (effectRows != 1){
            throw new BaseException("该比赛不存在或者已经被修改");
        }
        // 启用定时任务, 在比赛开始时修改题目状态, 改为 OPENING 状态
    }


    /**
     * 未结束的比赛列表
     */
    @Override
    public PageResult<ContestListVo> getUnfinishContest(PageQuery pageQuery, LoginUser loginUser) {

        Page<Contest> page = pageFactory.create(pageQuery);
        Page<Contest> contestPage = contestMapper.selectPage(
                page,
                Wrappers.<Contest>lambdaQuery()
                        .in(Contest::getStatus, ContestStatus.SCHEDULED, ContestStatus.RUNNING)
                        .ge(Contest::getEndTime, LocalDateTime.now())
                        .orderByAsc(Contest::getId)
        );

        IPage<ContestListVo> res = contestPage.convert(contest -> {
            ContestListVo vo = BeanUtil.copyProperties(contest, ContestListVo.class);

            vo.setContestId(contest.getId());

            LambdaQueryWrapper<ContestParticipate> query =
                    Wrappers.<ContestParticipate>lambdaQuery()
                            .eq(ContestParticipate::getContestId, contest.getId());

            long number = participateMapper.selectCount(query);

            vo.setParticipateNumber((int) number);

            boolean isRegister = false;

            if(loginUser != null){
                query.eq(ContestParticipate::getUserId, loginUser.getUserId());
                isRegister = participateMapper.selectCount(query) == 1;
            }

            vo.setIsRegister(isRegister);

            return vo;
        });

        return PageResult.of(res);
    }


    /**
     * 已结束比赛列表
     */
    @Override
    public PageResult<ContestListVo> getFinishContest(PageQuery pageQuery, LoginUser loginUser) {
        Page<Contest> page = pageFactory.create(pageQuery);
        Page<Contest> contestPage = contestMapper.selectPage(
                page,
                Wrappers.<Contest>lambdaQuery()
                        .notIn(Contest::getStatus, ContestStatus.DRAFT, ContestStatus.SCHEDULED, ContestStatus.RUNNING)
                        .orderByAsc(Contest::getId)
        );

        IPage<ContestListVo> res = contestPage.convert(contest -> {
            ContestListVo vo = BeanUtil.copyProperties(contest, ContestListVo.class);

            vo.setContestId(contest.getId());

            LambdaQueryWrapper<ContestParticipate> query =
                    Wrappers.<ContestParticipate>lambdaQuery()
                            .eq(ContestParticipate::getContestId, contest.getId());

            long number = participateMapper.selectCount(query);

            vo.setParticipateNumber((int) number);

            boolean isRegister = false;

            if(loginUser != null){
                query.eq(ContestParticipate::getUserId, loginUser.getUserId());
                isRegister = participateMapper.selectCount(query) == 1;
            }

            vo.setIsRegister(isRegister);

            return vo;
        });

        return PageResult.of(res);
    }


    /**
     * 进入比赛详情页
     */
    @Override
    public ContestDetailVo getContestDetail(Long contestId) {
        Contest contest = contestMapper.selectOne(
                Wrappers.<Contest> lambdaQuery()
                        .eq(Contest::getId, contestId)
                        .notIn(Contest::getStatus, ContestStatus.DRAFT, ContestStatus.SCHEDULED)
        );
        if (contest == null){
            throw new BaseException("该比赛不存在");
        }
        // 对于比赛未开始不允许获取比赛详情
        LocalDateTime now = LocalDateTime.now();
        if(contest.getStartTime().isAfter(now)){
            throw new BaseException("该比赛未开始");
        }

        long remain = Duration.between(now, contest.getEndTime()).getSeconds();

        if(contest.getEndTime().isBefore(now)){
            remain = 0L;
        }

        ContestDetailVo vo = new ContestDetailVo();
        vo.setContestId(contest.getId());
        vo.setTitle(contest.getTitle());
        vo.setDescription(contest.getDescription());
        vo.setRemainingSeconds(remain);
        vo.setStatus(contest.getStatus());

        List<ContestProblem> contestProblems = contestProblemMapper.selectList(
                Wrappers.<ContestProblem>lambdaQuery()
                        .eq(ContestProblem::getContestId, contestId)
                        .orderByAsc(ContestProblem::getSortOrder)
        );

        List<ContestProblemListVo> problems = new ArrayList<>();
        contestProblems.forEach(contestProblem ->
            problems.add(BeanUtil.copyProperties(contestProblem, ContestProblemListVo.class))
        );
        vo.setProblems(problems);

        return vo;
    }


    /**
     * 创建比赛草稿
     */
    @Override
    public Long createContestDraft(ContestDto dto) {
        if (
                dto.getRegisterStartTime().isAfter(dto.getRegisterEndTime()) ||
                dto.getStartTime().isAfter(dto.getEndTime()) ||
                dto.getRegisterEndTime().isAfter(dto.getEndTime())
        ) {
            throw new BaseException("报名/比赛时间不合法");
        }

        Contest contest = BeanUtil.copyProperties(dto, Contest.class);
        contest.setStatus(ContestStatus.DRAFT);

        contestMapper.insert(contest);
        log.info("添加比赛成功 {}:{}", contest.getId(), contest.getTitle());
        return contest.getId();
    }


    /**
     * 往比赛添加题目
     */
    @Override
    public void addProblem2Contest(AddProblem2ContestDto vo) {
        // 判断题目是否存在
        Problem problem = problemMapper.selectById(vo.getProblemId());
        if(problem == null){
            throw new BaseException("该问题不存在");
        }

        // 判断比赛是否存在, 目前只运行草稿阶段可以添加题目
        Contest contest = contestMapper.selectOne(
                Wrappers.<Contest> lambdaQuery()
                        .eq(Contest::getId, vo.getContestId())
                        .eq(Contest::getStatus, ContestStatus.DRAFT)
        );

        if(contest == null){
            throw new BaseException("该比赛不存在");
        }

        // 判断并获取该题目的测试数据
        ProblemTestData testData = testDataMapper.selectOne(
                Wrappers.<ProblemTestData>lambdaQuery()
                        .eq(ProblemTestData::getProblemId, vo.getProblemId())
                        .eq(ProblemTestData::getActive, 1)
                        .eq(ProblemTestData::getStatus, ProblemTestDataStatus.READY)
        );
        if(testData == null){
            throw new BaseException("找不到合适的测试数据");
        }

        // 通过关联表, 往比赛添加题目
        ContestProblem contestProblem = BeanUtil.copyProperties(vo, ContestProblem.class);
        contestProblem.setTestDataId(testData.getId());
        contestProblem.setReleaseStatus(ContestProblemStatus.HIDDEN);

        contestProblemMapper.insert(contestProblem);

        log.info("添加题目成功 problem:{} -> contest:{}",
                problem.getProblemName(),
                contest.getTitle()
        );
    }


    /**
     * 更新比赛信息
     */
    @Override
    public void updateContestInfo(Long contestId, ContestDto dto) {
        // 校验时间是否合法
        if (
                dto.getRegisterStartTime().isAfter(dto.getRegisterEndTime()) ||
                        dto.getStartTime().isAfter(dto.getEndTime()) ||
                        dto.getRegisterEndTime().isAfter(dto.getEndTime())
        ) {
            throw new BaseException("报名/比赛时间不合法");
        }

        Contest contest = BeanUtil.copyProperties(dto, Contest.class);
        contest.setId(contestId);
        contestMapper.updateById(contest);
    }


    /**
     * 获取比赛题目具体信息
     */
    @Override
    public ContestProblemDetailVo getContestProblemDetail(Long contestId, String problemCode) {
        // 可以判断这个题目是否归属这个比赛, 如果比赛时进行中的则该题目状态是opening的
        ContestProblem contestProblem = contestProblemMapper.selectOne(
                Wrappers.<ContestProblem>lambdaQuery()
                        .eq(ContestProblem::getReleaseStatus, ContestProblemStatus.OPENING)
                        .eq(ContestProblem::getContestId, contestId)
                        .eq(ContestProblem::getDisplayCode, problemCode)
        );
        if(contestProblem == null){
            throw new BaseException("不存在该题目");
        }

        Problem problem = problemMapper.selectById(contestProblem.getProblemId());

        if (problem == null){
            throw new BaseException("该题目不存在");
        }

        return BeanUtil.copyProperties(problem, ContestProblemDetailVo.class);
    }

}
