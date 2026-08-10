package com.gusl.gojserver.handler;

import cn.hutool.json.JSONUtil;
import com.gusl.common.common.Result;
import com.gusl.common.constant.HttpStatus;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 已登录但权限不足处理类
 */
@Component
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        response.setStatus(HttpStatus.FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        Result result = new Result(HttpStatus.FORBIDDEN, "权限不足, 无法访问", null);
        response.getWriter().write(String.valueOf(JSONUtil.parseObj(result)));
    }
}
