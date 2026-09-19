package com.gusl.gojserver.profile.application;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.common.common.PageQuery;
import com.gusl.common.common.PageResult;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.gojserver.problem.infrastructure.persistence.ProblemMapper;
import com.gusl.gojserver.problem.infrastructure.persistence.TagMapper;
import com.gusl.gojserver.profile.infrastructure.persistence.UserProblemProgressMapper;
import com.gusl.gojserver.account.infrastructure.security.LoginUser;
import com.gusl.common.pojo.entity.UserProblemProgress;
import com.gusl.gojserver.problem.application.vo.ProblemPageListVo;
import com.gusl.gojserver.shared.application.pagination.PageFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserProblemProgressServiceImpl extends ServiceImpl<UserProblemProgressMapper, UserProblemProgress> implements UserProblemProgressService {

    private final UserProblemProgressMapper problemProgressMapper;

    private final ProblemMapper problemMapper;

    private final TagMapper tagMapper;

    private final PageFactory pageFactory;

    /**
     * 我通过的题目
     */
    @Override
    public PageResult<ProblemPageListVo> getSolveByMe(PageQuery pageQuery, LoginUser loginUser) {

        Page<UserProblemProgress> page = pageFactory.create(pageQuery);

        Page<UserProblemProgress> userProblemProgresses = problemProgressMapper.selectPage(
                page,
                Wrappers.<UserProblemProgress> lambdaQuery()
                        .eq(UserProblemProgress::getUserId, loginUser.getUserId())
                        .isNotNull(UserProblemProgress::getFirstAcceptedTime)
        );

        IPage<ProblemPageListVo> res = userProblemProgresses.convert(progress -> {
            ProblemPageListVo vo = new ProblemPageListVo();
            Problem problem = problemMapper.selectById(progress.getProblemId());
            BeanUtil.copyProperties(problem, vo);
            vo.setProblemId(problem.getId());
            vo.setTags(tagMapper.getTagByProblemId(progress.getProblemId()));
            vo.setStatus(progress.getStatus());
           return vo;
        });

        return PageResult.of(res);
    }

    /**
     * 我尝试过的题目
     */
    @Override
    public PageResult<ProblemPageListVo> getAttemptedProblem(PageQuery pageQuery, LoginUser loginUser) {

        Page<UserProblemProgress> page = pageFactory.create(pageQuery);

        Page<UserProblemProgress> userProblemProgresses = problemProgressMapper.selectPage(
                page,
                Wrappers.<UserProblemProgress> lambdaQuery()
                        .eq(UserProblemProgress::getUserId, loginUser.getUserId())
                        .isNull(UserProblemProgress::getFirstAcceptedTime)
        );

        IPage<ProblemPageListVo> res = userProblemProgresses.convert(progress -> {
            ProblemPageListVo vo = new ProblemPageListVo();
            Problem problem = problemMapper.selectById(progress.getProblemId());
            BeanUtil.copyProperties(problem, vo);
            vo.setProblemId(problem.getId());
            vo.setTags(tagMapper.getTagByProblemId(progress.getProblemId()));
            vo.setStatus(progress.getStatus());
            return vo;
        });

        return PageResult.of(res);
    }


}
