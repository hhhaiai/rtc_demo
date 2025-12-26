package com.phoenix.rtc.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT 配置和工具类
 * 用于生成和验证 JWT Token
 */
@Component
@Slf4j
public class JwtConfig {

    @Value("${JWT_SECRET_KEY}")
    private String secretKey;

    /**
     * 启动时检查密钥配置
     */
    @PostConstruct
    public void validateConfig() {
        if (secretKey == null || secretKey.trim().isEmpty()) {
            throw new IllegalStateException("JWT_SECRET_KEY 环境变量未配置，系统无法启动！请设置至少 32 字符的密钥。");
        }
        if (secretKey.length() < 32) {
            log.warn("JWT_SECRET_KEY 长度不足 32 字符，建议增加长度以确保安全性");
        }
        log.info("JWT 密钥配置验证通过");
    }

    @Value("${jwt.expiration:7200000}") // 2小时
    private long expiration;

    private SecretKey getSigningKey() {
        // 确保密钥至少256位
        byte[] keyBytes = secretKey.getBytes();
        if (keyBytes.length < 32) {
            // 填充到32字节(256位)
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 JWT Token
     */
    public String generateToken(String userId, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 从 Token 中提取用户ID
     */
    public String extractUserId(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getSubject();
        } catch (Exception e) {
            log.error("解析 Token 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 Token 中提取角色
     */
    public String extractRole(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.get("role", String.class);
        } catch (Exception e) {
            log.error("解析 Token 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 验证 Token
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            log.error("Token 验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 解析 Token 声明
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 获取 Token 剩余有效期(毫秒)
     */
    public long getRemainingTime(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getExpiration().getTime() - System.currentTimeMillis();
        } catch (Exception e) {
            return 0;
        }
    }
}
