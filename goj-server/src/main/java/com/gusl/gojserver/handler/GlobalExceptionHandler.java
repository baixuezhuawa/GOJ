package com.gusl.gojserver.handler;


import com.gusl.common.common.BaseException;
import com.gusl.common.common.Result;
import com.gusl.common.constant.HttpStatus;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    /**
     * 键重复异常
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result> handleDuplicateKeyException(DuplicateKeyException de){
        return ResponseEntity
                .status(400)
                .body(new Result(HttpStatus.CONFLICT, "键冲突: " + de, null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("请求参数不合法");

        return ResponseEntity
                .badRequest()
                .body(new Result(HttpStatus.BAD_REQUEST, message, null));
    }

    /**
     * 处理方法参数校验异常，例如路径变量和请求参数上的约束校验。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result> handleConstraintViolationException(
            ConstraintViolationException exception
    ) {
        String message = exception.getConstraintViolations()
                .stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("请求参数不合法");

        return ResponseEntity
                .badRequest()
                .body(new Result(HttpStatus.BAD_REQUEST, message, null));
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
