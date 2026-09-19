package com.gusl.gojserver.profile.application;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.gojserver.account.infrastructure.security.LoginUser;
import com.gusl.common.pojo.entity.UserSubmissionStatusStat;
import com.gusl.gojserver.profile.application.vo.SubmissionStatusStatVo;

import java.util.List;

public interface UserSubmissionStatusStatService extends IService<UserSubmissionStatusStat> {

    /**
     * 获取我不同测评状态统计
     */
    List<SubmissionStatusStatVo> getMySubmissionStatusStat(LoginUser loginUser);
}
