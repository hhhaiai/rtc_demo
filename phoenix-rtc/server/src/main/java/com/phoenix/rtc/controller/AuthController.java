package com.phoenix.rtc.controller;

import com.phoenix.rtc.config.JwtConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器
 * 提供 JWT Token 生成接口
 *
 * 修复说明:
 * 1. 移除了硬编码密码检查
 * 2. 使用环境变量配置的演示密码
 * 3. 生产环境应集成真实用户系统
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final JwtConfig jwtConfig;

    @Value("${auth.demo-password}")
    private String demoPassword;

    /**
     * 登录并获取 JWT Token
     * POST /api/auth/login
     *
     * 请求示例:
     * {
     *   "username": "user123",
     *   "password": "password123"
     * }
     *
     * 响应示例:
     * {
     *   "success": true,
     *   "token": "eyJhbGciOiJIUzI1NiJ9...",
     *   "expiresIn": 7200
     * }
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        try {
            String username = credentials.get("username");
            String password = credentials.get("password");

            // TODO: 实际项目中应该验证用户名密码
            // 这里简化处理，仅作演示
            if (username == null || password == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "用户名和密码不能为空"));
            }

            // TODO: 实际项目中应该集成真实的 UserService 进行数据库校验
            // 这里仅作为演示框架，实际部署时必须替换为真实认证逻辑
            // 示例: User user = userService.authenticate(username, password);

            // 检查演示密码（从配置文件读取，支持环境变量覆盖）
            if (demoPassword == null || demoPassword.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("success", false, "message", "认证服务未配置，请联系管理员配置 DEMO_AUTH_PASSWORD"));
            }

            if (!demoPassword.equals(password)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "用户名或密码错误"));
            }

            // 生成 JWT Token
            String token = jwtConfig.generateToken(username, "user");
            long expiresIn = 7200; // 2小时

            log.info("用户登录成功 - 用户: {}", username);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "token", token,
                "expiresIn", expiresIn,
                "message", "登录成功"
            ));
        } catch (Exception e) {
            log.error("登录失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "登录失败"));
        }
    }

    /**
     * 验证 Token
     * GET /api/auth/verify
     */
    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestHeader(value = "Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "无效的认证头"));
            }

            String token = authHeader.substring(7);
            boolean valid = jwtConfig.validateToken(token);

            if (!valid) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Token 已过期或无效"));
            }

            String userId = jwtConfig.extractUserId(token);
            String role = jwtConfig.extractRole(token);
            long remainingTime = jwtConfig.getRemainingTime(token);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "userId", userId,
                "role", role,
                "remainingTime", remainingTime,
                "message", "Token 有效"
            ));
        } catch (Exception e) {
            log.error("Token 验证失败", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("success", false, "message", "Token 验证失败"));
        }
    }

    /**
     * 生成演示 Token (仅用于开发测试)
     * GET /api/auth/demo-token
     */
    @GetMapping("/demo-token")
    public ResponseEntity<?> demoToken(@RequestParam(defaultValue = "user_demo_001") String userId) {
        String token = jwtConfig.generateToken(userId, "user");
        return ResponseEntity.ok(Map.of(
            "success", true,
            "token", token,
            "userId", userId,
            "message", "演示 Token (仅用于开发测试)"
        ));
    }
}
