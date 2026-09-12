package com.gusl.gojserver.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.common.pojo.entity.UserLanguageStat;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.vo.LanguageStatVo;

import java.util.List;

/**
 * 用户语言统计服务，提供当前用户语言使用情况查询。
 */
public interface UserLanguageStatService extends IService<UserLanguageStat> {

    /**
     * 获取当前用户的语言使用统计。
     *
     * @param loginUser 当前登录用户
     * @return 语言使用统计列表
     */
    List<LanguageStatVo> getMyLanguageStat(LoginUser loginUser);
}
