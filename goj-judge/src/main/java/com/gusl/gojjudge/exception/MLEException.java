package com.gusl.gojjudge.exception;

/**
 * 内存超出限制异常
 */
public class MLEException extends RuntimeException {
    public MLEException(String message) {
        super(message);
    }
}
