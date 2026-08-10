package com.gusl.gojserver.pojo.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

@Data
public class LoginUser implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户基本信息
     */
    private User user;

    /**
     * 登录ip地址
     */
    private String ipaddr;

    /**
     * 操作系统
     */
    private String os;

    /**
     * 浏览器
     */
    private String browser;

    /**
     * 登录时间，毫秒时间戳
     */
    private Long loginTime;

    /**
     * 登录过期时间，毫秒时间戳
     */
    private Long expireTime;

    /**
     * 登录的唯一标识
     */
    private String loginId;

    /**
     * 当前用户拥有的权限编码
     */
    private Set<String> permissions;

    public LoginUser(){

    }

    public LoginUser(User user, Set<String> permissions){
        this.user = user;
        this.permissions = permissions;
    }


    /**
     * 将权限字符串转换成 Spring Security 权限对象
     */
    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (permissions == null || permissions.isEmpty()) {
            return Collections.emptyList();
        }

        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    /**
     * 获取账号密码
     * @return 密码
     */
    @Override
    public @Nullable String getPassword() {
        return user.getPassword();
    }

    /**
     * 获取用户名
     * @return 用户名
     */
    @Override
    public String getUsername() {
        return user.getUsername();
    }

    /**
     * 判断账号是否过期
     * @return 没有过期
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 判断账号是否没有锁定
     * @return 没有锁定
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 密码是否没有过期
     * @return 密码没有过期
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 用户是否启用
     * @return 状态
     */
    @Override
    public boolean isEnabled() {
        return Integer.valueOf(1).equals(user.getStatus());
    }

    public Long getUserId(){
        return user.getId();
    }
}
