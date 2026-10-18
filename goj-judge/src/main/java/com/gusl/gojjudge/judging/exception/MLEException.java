package com.gusl.gojjudge.judging.exception;

/**
 * 内存超出限制异常
 */
public class MLEException extends RuntimeException {
    public MLEException(String message) {
        super(message);
    }
}
