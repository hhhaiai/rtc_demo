package com.phoenix.rtc.aspect;

import com.phoenix.rtc.config.JwtConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;

/**
 * 限流切面
 * 基于 Redis 实现用户级别的速率限制
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitAspect {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtConfig jwtConfig;

    // 限制规则: 每60秒最多10次请求
    private static final int MAX_REQUESTS = 10;
    private static final int TIME_WINDOW = 60; // 秒

    /**
     * 在 RTC 控制器方法执行前进行限流检查
     */
    @Before("execution(* com.phoenix.rtc.controller.RtcController.*(..))")
    public void rateLimitCheck() {
        HttpServletRequest request = ((ServletRequestAttributes)
            RequestContextHolder.currentRequestAttributes()).getRequest();

        // 从 Header 获取用户ID (JWT Token)
        String authHeader = request.getHeader("Authorization");
        String userId = extractUserIdFromHeader(authHeader);

        if (userId == null) {
            return; // 未认证用户不进行限流
        }

        String redisKey = String.format("ratelimit:rtc:%s", userId);

        // 获取当前计数
        Integer currentCount = (Integer) redisTemplate.opsForValue().get(redisKey);

        if (currentCount == null) {
            // 第一次请求，设置计数和过期时间
            redisTemplate.opsForValue().set(redisKey, 1, TIME_WINDOW, TimeUnit.SECONDS);
            return;
        }

        if (currentCount >= MAX_REQUESTS) {
            // 超过限制
            Long ttl = redisTemplate.getExpire(redisKey);
            throw new RateLimitException("请求过于频繁，请稍后再试。限制: " + MAX_REQUESTS +
                "次/" + TIME_WINDOW + "秒。剩余时间: " + ttl + "秒");
        }

        // 计数+1
        redisTemplate.opsForValue().increment(redisKey);
    }

    private String extractUserIdFromHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);

        // 使用 JWT 配置解析用户ID
        if (jwtConfig.validateToken(token)) {
            return jwtConfig.extractUserId(token);
        }

        return null;
    }

    /**
     * 限流异常
     */
    public static class RateLimitException extends RuntimeException {
        public RateLimitException(String message) {
            super(message);
        }
    }
}
