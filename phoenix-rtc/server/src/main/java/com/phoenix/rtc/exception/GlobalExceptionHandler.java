package com.phoenix.rtc.exception;

import com.phoenix.rtc.aspect.RateLimitAspect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理限流异常
     */
    @ExceptionHandler(RateLimitAspect.RateLimitException.class)
    public ResponseEntity<?> handleRateLimitException(RateLimitAspect.RateLimitException ex) {
        log.warn("限流拦截: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(Map.of(
                "success", false,
                "code", "RATE_LIMIT_EXCEEDED",
                "message", ex.getMessage(),
                "retryAfter", 60
            ));
    }

    /**
     * 处理认证异常
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<?> handleUnauthorizedException(UnauthorizedException ex) {
        log.warn("认证失败: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of(
                "success", false,
                "code", "UNAUTHORIZED",
                "message", ex.getMessage()
            ));
    }

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handleBusinessException(BusinessException ex) {
        log.warn("业务异常: {}", ex.getMessage());
        return ResponseEntity.badRequest()
            .body(Map.of(
                "success", false,
                "code", "BUSINESS_ERROR",
                "message", ex.getMessage()
            ));
    }

    /**
     * 处理其他未预期异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {
        log.error("系统异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of(
                "success", false,
                "code", "INTERNAL_ERROR",
                "message", "服务器内部错误"
            ));
    }
}
