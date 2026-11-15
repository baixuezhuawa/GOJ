package com.gusl.gojjudge.judging.exception;

/**
 * 系统运行异常
 */
public class SystemErrorException extends RuntimeException {
    public SystemErrorException(String message) {
        super(message);
    }
    public SystemErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
