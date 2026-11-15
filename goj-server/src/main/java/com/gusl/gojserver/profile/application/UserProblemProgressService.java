package com.gusl.gojserver.profile.application;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.common.common.PageQuery;
import com.gusl.common.common.PageResult;
import com.gusl.gojserver.account.infrastructure.security.LoginUser;
import com.gusl.common.pojo.entity.UserProblemProgress;
import com.gusl.gojserver.problem.application.vo.ProblemPageListVo;

public interface UserProblemProgressService extends IService<UserProblemProgress> {

    PageResult<ProblemPageListVo> getSolveByMe(PageQuery pageQuery, LoginUser loginUser);

    PageResult<ProblemPageListVo> getAttemptedProblem(PageQuery pageQuery, LoginUser loginUser);
}
