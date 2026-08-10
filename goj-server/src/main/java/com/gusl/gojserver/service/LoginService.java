package com.gusl.gojserver.service;

import com.gusl.common.common.BaseException;
import com.gusl.common.utils.StringUtils;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.mapper.UserMapper;
import com.gusl.gojserver.pojo.dto.UserLoginDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private final UserMapper userMapper;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    /**
     * 登录
     * @param loginUserDto 登录信息
     * @return 登录 token
     */
    public String doLogin(UserLoginDto loginUserDto){
        if(StringUtils.isEmpty(loginUserDto.getUsername(), loginUserDto.getPassword())){
            throw new BaseException("账号和密码不能为空");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginUserDto.getUsername(), loginUserDto.getPassword())
                );

        LoginUser loginUser = (LoginUser) authentication.getPrincipal();

        assert loginUser != null;
        return tokenService.createToken(loginUser);
    }

    /**
     * 退出登录
     */
    public void doLogout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if(authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser){
            tokenService.deleteLoginUser(loginUser.getLoginId());
        }

        SecurityContextHolder.clearContext();
    }
}
