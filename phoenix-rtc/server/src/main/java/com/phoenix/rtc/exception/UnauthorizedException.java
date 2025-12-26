package com.phoenix.rtc.exception;

/**
 * 认证异常
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
