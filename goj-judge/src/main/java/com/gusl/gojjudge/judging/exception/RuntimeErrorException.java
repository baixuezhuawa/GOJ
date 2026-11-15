package com.gusl.gojjudge.judging.exception;

/**
 * 代码运行错误
 */
public class RuntimeErrorException extends RuntimeException {
    public RuntimeErrorException(String message) {
        super(message);
    }
}
