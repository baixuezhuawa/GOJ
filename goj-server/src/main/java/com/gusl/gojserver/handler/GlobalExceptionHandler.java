package com.gusl.gojserver.handler;


import com.gusl.common.common.BaseException;
import com.gusl.common.common.Result;
import com.gusl.common.constant.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.security.core.AuthenticationException;

/**
 * 全局异常处理类
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 权限不足
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result> handleAccessDeniedException(AccessDeniedException exception) {

        return ResponseEntity
                .status(403)
                .body(new Result(HttpStatus.NOT_PERMISSION, "权限不足，无法访问该接口", null));
    }

    /**
     * 账号密码错误
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result> handleAuthenticationException() {
        return ResponseEntity
                .status(401)
                .body(new Result(HttpStatus.UNAUTHORIZED, "用户名或密码错误", null));
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<Result> handleBaseException(BaseException exception) {
        return ResponseEntity
                .badRequest()
                .body(new Result(HttpStatus.BAD_REQUEST, exception.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result> handleException(Exception exception) {
        return ResponseEntity
                .status(500)
                .body(new Result(HttpStatus.ERROR, "系统内部错误: " + exception.getMessage(), null));
    }
    
    
}