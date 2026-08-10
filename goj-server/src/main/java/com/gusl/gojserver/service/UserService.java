package com.gusl.gojserver.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.gojserver.pojo.entity.User;
import com.gusl.gojserver.pojo.dto.UserRegisterDto;

public interface UserService extends IService<User> {

    void doRegister(UserRegisterDto registerDto);

}
