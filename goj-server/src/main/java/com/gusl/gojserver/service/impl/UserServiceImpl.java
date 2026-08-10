package com.gusl.gojserver.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.common.common.BaseException;
import com.gusl.common.utils.StringUtils;
import com.gusl.gojserver.pojo.entity.Role;
import com.gusl.gojserver.pojo.entity.User;
import com.gusl.gojserver.mapper.RoleMapper;
import com.gusl.gojserver.mapper.UserMapper;
import com.gusl.gojserver.mapper.UserRoleMapper;
import com.gusl.gojserver.pojo.dto.UserRegisterDto;
import com.gusl.gojserver.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void doRegister(UserRegisterDto registerDto) {
        // 判断账号是否规范
        if(StringUtils.isEmpty(registerDto.getUsername())){
            throw new BaseException("账号名不能为空");
        }

        // 校验密码
        if(StringUtils.isEmpty(registerDto.getPassword(), registerDto.getConfirmPassword()) || !registerDto.getPassword().equals(registerDto.getConfirmPassword())){
            throw new BaseException("密码不能为空或者两次秘密不一致");
        }

        // 判断账号是否已经存在
        int count = userMapper.CountUserByUsername(registerDto.getUsername());
        if(count > 0){
            throw new BaseException("该账号已存在");
        }

        // 加密密码
        registerDto.setPassword(passwordEncoder.encode(registerDto.getPassword()));

        User user = new User();
        // 属性拷贝
        BeanUtil.copyProperties(registerDto, user);

        // 授予USER角色权限
        List<Role> roles = roleMapper.selectByMap(Map.of("role_code", "USER"));
        Long userRoleId = 2L;
        if(!CollectionUtil.isEmpty(roles)){
            userRoleId = roles.get(0).getId();
        }

        // 注册
        userMapper.insert(user);
        // 默认权限, 普通用户
        userRoleMapper.insert(user.getId(), userRoleId);
    }
}
