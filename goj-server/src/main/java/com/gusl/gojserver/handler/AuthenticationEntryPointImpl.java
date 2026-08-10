package com.gusl.gojserver.handler;

import cn.hutool.json.JSONUtil;
import com.gusl.common.common.Result;
import com.gusl.common.constant.HttpStatus;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 未登录处理类
 */
@Component
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        response.setStatus(HttpStatus.UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        Result result = new Result(HttpStatus.UNAUTHORIZED, "未登录或者登录状态已过期", null);
        response.getWriter().write(String.valueOf(JSONUtil.parse(result)));
    }
}
