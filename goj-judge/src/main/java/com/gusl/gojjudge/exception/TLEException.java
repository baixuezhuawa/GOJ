package com.gusl.gojjudge.exception;

/**
 * 时间超出限制异常
 */
public class TLEException extends RuntimeException {
    public TLEException(String message) {
        super(message);
    }
}
