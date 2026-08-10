package com.gusl.gojserver.controller;

import com.gusl.common.common.BaseController;
import com.gusl.common.common.Result;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.dto.UserLoginDto;
import com.gusl.gojserver.pojo.dto.UserRegisterDto;
import com.gusl.gojserver.service.LoginService;
import com.gusl.gojserver.service.UserService;
import com.gusl.gojserver.pojo.vo.CurrentUserVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@Tag(name = "用户中心")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController extends BaseController {

    private final UserService userService;
    private final LoginService loginService;

    /**
     * 用户注册
     * @param registerDto 用户信息
     * @return 注册成功
     */
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result register(@RequestBody UserRegisterDto registerDto){
        userService.doRegister(registerDto);
        return success("注册成功");
    }

    /**
     * 用户登录
     * @param loginDto 用户登录信息
     * @return 登录成功
     */
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result login(@RequestBody UserLoginDto loginDto){
        String token = loginService.doLogin(loginDto);
        return success("登录成功", Map.of("token", token));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result logout(){
        loginService.doLogout();
        return success("退出登录成功");
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/me")
    public Result getMe(@AuthenticationPrincipal LoginUser loginUser) {

        return success("查询成功", new CurrentUserVo(
                loginUser.getUserId(),
                loginUser.getUsername(),
                loginUser.getPermissions()
        ));
    }
}
