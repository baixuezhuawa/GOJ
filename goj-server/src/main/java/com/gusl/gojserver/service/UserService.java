package com.gusl.gojserver.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.entity.User;
import com.gusl.gojserver.pojo.dto.UserRegisterDto;
import com.gusl.gojserver.pojo.entity.UserProfile;

public interface UserService extends IService<User> {

    void doRegister(UserRegisterDto registerDto);

    UserProfile getMyProfile(LoginUser loginUser);

    void setMyProfile(UserProfile profile, LoginUser loginUser);
}
