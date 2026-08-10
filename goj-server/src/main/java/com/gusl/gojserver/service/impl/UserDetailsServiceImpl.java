package com.gusl.gojserver.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.gusl.common.common.BaseException;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.entity.User;
import com.gusl.gojserver.mapper.PermissionMapper;
import com.gusl.gojserver.mapper.RoleMapper;
import com.gusl.gojserver.mapper.UserMapper;
import com.gusl.gojserver.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 根据账号校验用户
 * 判断是否需要
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;
    private final PermissionMapper permissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    @Override
    public UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        User user = userMapper.getUserByUsername(username);

        if(ObjectUtil.isEmpty(user)){
            log.info("账号 {} 不存在", username);
            throw new UsernameNotFoundException("用户不存在");
        }

        if(Integer.valueOf(0).equals(user.getStatus())){
            log.info("账号 {} 被停用", username);
            throw new BaseException("该账号已被禁用");
        }

        Set<String> permissions;

        // 如果是超级管理员, 则获取全部权限
        Set<String> roles = roleMapper.getRoleCodeByUserId(user.getId());

        if(roles.contains("SUPER_ADMIN")) {
            permissions = permissionMapper.getAllPermission();
        }else {
            permissions = permissionMapper.getPermissionCodesByUserId(user.getId());
        }
        return new LoginUser(user, permissions);
    }
}
