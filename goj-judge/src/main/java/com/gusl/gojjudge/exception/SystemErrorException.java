package com.gusl.gojjudge.exception;

/**
 * 系统运行异常
 */
public class SystemErrorException extends RuntimeException {
    public SystemErrorException(String message) {
        super(message);
    }
}
