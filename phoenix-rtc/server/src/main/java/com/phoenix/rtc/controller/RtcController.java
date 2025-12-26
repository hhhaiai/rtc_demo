package com.phoenix.rtc.controller;

import com.phoenix.rtc.aspect.RateLimitAspect;
import com.phoenix.rtc.config.JwtConfig;
import com.phoenix.rtc.model.dto.CallRequest;
import com.phoenix.rtc.model.dto.JoinRequest;
import com.phoenix.rtc.model.dto.TokenResponse;
import com.phoenix.rtc.model.dto.WSMessage;
import com.phoenix.rtc.service.RoomService;
import com.phoenix.rtc.service.StateManagementService;
import com.phoenix.rtc.service.WebSocketService;
import com.phoenix.rtc.statemachine.CallState;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * RTC 业务控制器
 * 处理通话相关的 REST API 请求
 */
@RestController
@RequestMapping("/api/rtc")
@RequiredArgsConstructor
@Slf4j
public class RtcController {

    private final RoomService roomService;
    private final WebSocketService webSocketService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtConfig jwtConfig;
    private final StateManagementService stateManagementService;

    /**
     * 发起通话
     * POST /api/rtc/call/start
     */
    @PostMapping("/call/start")
    public ResponseEntity<?> startCall(@Valid @RequestBody CallRequest request,
                                      @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            // 从认证上下文获取当前用户ID
            String currentUserId = extractUserIdFromAuth(authHeader);
            if (currentUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "未授权的访问"));
            }

            // 验证请求参数
            if (request.getTargetUserIds() == null || request.getTargetUserIds().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "至少需要一个目标用户"));
            }

            if (request.getSessionType() == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "通话类型不能为空"));
            }

            // 检查状态 - 是否可以发起呼叫
            if (!stateManagementService.canStartCall(null, currentUserId)) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "当前正在通话中，无法发起新呼叫"));
            }

            TokenResponse response = roomService.startCall(request, currentUserId);

            // 设置状态为 CALLING
            stateManagementService.startCalling(response.getRoomName(), currentUserId);

            // 通过 WebSocket 通知被叫用户
            for (String targetUserId : request.getTargetUserIds()) {
                webSocketService.sendInvite(
                        targetUserId,
                        currentUserId,
                        "用户" + currentUserId,
                        response.getRoomName(),
                        request.getSessionType(),
                        request.getTitle()
                );
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response,
                    "message", "通话已发起，正在等待对方接听",
                    "state", CallState.CALLING.getCode()
            ));
        } catch (IllegalArgumentException e) {
            log.warn("参数验证失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("发起通话失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "服务器内部错误"));
        }
    }

    /**
     * 加入通话
     * POST /api/rtc/call/join
     */
    @PostMapping("/call/join")
    public ResponseEntity<?> joinCall(@Valid @RequestBody JoinRequest request,
                                     @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String currentUserId = extractUserIdFromAuth(authHeader);
            if (currentUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "未授权的访问"));
            }

            if (request.getRoomName() == null || request.getRoomName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "房间名称不能为空"));
            }

            // 设置状态为 CONNECTING
            stateManagementService.acceptCall(request.getRoomName(), currentUserId);

            TokenResponse response = roomService.joinCall(request.getRoomName(), currentUserId);

            // 设置状态为 CONNECTED
            stateManagementService.connected(request.getRoomName(), currentUserId);

            // 通知发起方
            String initiatorId = (String) redisTemplate.opsForHash().get(String.format("rtc:room:%s:meta", request.getRoomName()), "initiatorId");
            if (initiatorId != null) {
                webSocketService.notifyPeerAccepted(initiatorId, currentUserId, request.getRoomName());
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response,
                    "message", "成功加入通话",
                    "state", CallState.CONNECTED.getCode()
            ));
        } catch (IllegalArgumentException e) {
            log.warn("参数验证失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("加入通话失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "服务器内部错误"));
        }
    }

    /**
     * 离开通话
     * POST /api/rtc/call/leave
     */
    @PostMapping("/call/leave")
    public ResponseEntity<?> leaveCall(@RequestBody Map<String, String> request,
                                      @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String currentUserId = extractUserIdFromAuth(authHeader);
            if (currentUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "未授权的访问"));
            }

            String roomName = request.get("roomName");
            if (roomName == null || roomName.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "房间名称不能为空"));
            }

            // 设置状态为 ENDED
            stateManagementService.endCall(roomName, currentUserId);

            roomService.leaveCall(roomName, currentUserId);

            // 通知其他用户
            webSocketService.sendToRoom(roomName, WSMessage.success("peer_left", Map.of(
                    "userId", currentUserId,
                    "roomName", roomName
            )));

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "已离开通话",
                    "state", CallState.ENDED.getCode()
            ));
        } catch (IllegalArgumentException e) {
            log.warn("参数验证失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("离开通话失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "服务器内部错误"));
        }
    }

    /**
     * 查询房间信息
     * GET /api/rtc/room/{roomName}
     */
    @GetMapping("/room/{roomName}")
    public ResponseEntity<?> getRoomInfo(@PathVariable String roomName,
                                        @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String currentUserId = extractUserIdFromAuth(authHeader);
            if (currentUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "未授权的访问"));
            }

            if (roomName == null || roomName.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "房间名称不能为空"));
            }

            var session = roomService.getRoomInfo(roomName);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", session
            ));
        } catch (IllegalArgumentException e) {
            log.warn("参数验证失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("查询房间信息失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "服务器内部错误"));
        }
    }

    /**
     * 查询用户当前通话
     * GET /api/rtc/user/current
     */
    @GetMapping("/user/current")
    public ResponseEntity<?> getCurrentSessions(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String currentUserId = extractUserIdFromAuth(authHeader);
            if (currentUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "未授权的访问"));
            }

            var sessions = roomService.getActiveSessions(currentUserId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", sessions
            ));
        } catch (Exception e) {
            log.error("查询当前通话失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "服务器内部错误"));
        }
    }

    /**
     * 查询通话状态
     * GET /api/rtc/state/{roomName}
     */
    @GetMapping("/state/{roomName}")
    public ResponseEntity<?> getCallState(@PathVariable String roomName,
                                         @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String currentUserId = extractUserIdFromAuth(authHeader);
            if (currentUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "未授权的访问"));
            }

            if (roomName == null || roomName.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "房间名称不能为空"));
            }

            CallState state = stateManagementService.getCurrentState(roomName, currentUserId);
            java.util.List<String> history = stateManagementService.getStateHistory(roomName, currentUserId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                        "roomName", roomName,
                        "userId", currentUserId,
                        "currentState", state.getCode(),
                        "stateDescription", state.getDescription(),
                        "history", history
                    )
            ));
        } catch (Exception e) {
            log.error("查询通话状态失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "服务器内部错误"));
        }
    }

    /**
     * 从认证头中提取用户ID
     */
    private String extractUserIdFromAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        String token = authHeader.substring(7);

        // 验证 Token
        if (!jwtConfig.validateToken(token)) {
            log.warn("无效的 Token: {}", token.substring(0, Math.min(10, token.length())) + "...");
            return null;
        }

        // 提取用户ID
        String userId = jwtConfig.extractUserId(token);
        if (userId == null) {
            log.warn("无法从 Token 中提取用户ID");
            return null;
        }

        log.debug("JWT 认证成功 - 用户: {}", userId);
        return userId;
    }
}
