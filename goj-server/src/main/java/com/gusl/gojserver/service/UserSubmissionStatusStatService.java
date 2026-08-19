package com.gusl.gojserver.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.common.pojo.entity.UserSubmissionStatusStat;
import com.gusl.gojserver.pojo.vo.SubmissionStatusStatVo;

import java.util.List;

public interface UserSubmissionStatusStatService extends IService<UserSubmissionStatusStat> {

    /**
     * 获取我不同测评状态统计
     */
    List<SubmissionStatusStatVo> getMySubmissionStatusStat(LoginUser loginUser);
}
