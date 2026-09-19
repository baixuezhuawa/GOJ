package com.gusl.gojjudge.judging.domain.exception;

/**
 * 答案错误异常
 */
public class WAException extends RuntimeException {
    public WAException(String message) {
        super(message);
    }
}
