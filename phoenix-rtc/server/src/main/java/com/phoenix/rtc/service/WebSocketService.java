package com.phoenix.rtc.service;

import com.phoenix.rtc.model.dto.WSMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 服务
 * 负责处理 WebSocket 消息和推送
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String INVITE_KEY = "rtc:invite:%s";  // 邀请缓存 (Hash)

    /**
     * 推送消息给指定用户
     */
    public void sendToUser(String userId, WSMessage message) {
        String destination = "/user/" + userId + "/queue/rtc";
        messagingTemplate.convertAndSend(destination, message);
        log.debug("推送消息给用户 {}: {}", userId, message);
    }

    /**
     * 推送消息到房间所有用户
     */
    public void sendToRoom(String roomName, WSMessage message) {
        String destination = "/topic/room/" + roomName;
        messagingTemplate.convertAndSend(destination, message);
        log.debug("推送消息到房间 {}: {}", roomName, message);
    }

    /**
     * 推送呼叫邀请给被叫用户
     * 同时缓存邀请信息到 Redis
     */
    public void sendInvite(String targetUserId, String inviterId, String inviterName, String roomName, String mode, String title) {
        // 生成邀请ID
        String inviteId = UUID.randomUUID().toString();

        // 1. 缓存邀请信息到 Redis (TTL 5分钟)
        String inviteKey = String.format(INVITE_KEY, inviteId);
        redisTemplate.opsForHash().put(inviteKey, "inviteId", inviteId);
        redisTemplate.opsForHash().put(inviteKey, "targetUserId", targetUserId);
        redisTemplate.opsForHash().put(inviteKey, "inviterId", inviterId);
        redisTemplate.opsForHash().put(inviteKey, "inviterName", inviterName);
        redisTemplate.opsForHash().put(inviteKey, "roomName", roomName);
        redisTemplate.opsForHash().put(inviteKey, "mode", mode);
        redisTemplate.opsForHash().put(inviteKey, "title", title);
        redisTemplate.opsForHash().put(inviteKey, "timestamp", System.currentTimeMillis());
        redisTemplate.expire(inviteKey, 5, TimeUnit.MINUTES);

        // 2. 发送 WebSocket 消息
        WSMessage message = WSMessage.builder()
                .type("rtc")
                .cmd("ringing")
                .data(new InviteData(inviterId, inviterName, roomName, mode, title, inviteId))
                .timestamp(System.currentTimeMillis())
                .build();

        sendToUser(targetUserId, message);

        log.info("发送邀请并缓存 - 邀请ID: {}, 被叫: {}, 房间: {}", inviteId, targetUserId, roomName);
    }

    /**
     * 通知发起方被叫已接听
     */
    public void notifyPeerAccepted(String initiatorId, String targetUserId, String roomName) {
        WSMessage message = WSMessage.builder()
                .type("rtc")
                .cmd("peer_accepted")
                .data(new AcceptData(targetUserId, roomName))
                .timestamp(System.currentTimeMillis())
                .build();

        sendToUser(initiatorId, message);
    }

    /**
     * 通知用户对方已离开
     */
    public void notifyPeerLeft(String userId, String peerId, String roomName) {
        WSMessage message = WSMessage.builder()
                .type("rtc")
                .cmd("peer_left")
                .data(new LeaveData(peerId, roomName))
                .timestamp(System.currentTimeMillis())
                .build();

        sendToUser(userId, message);
    }

    /**
     * 推送错误消息
     */
    public void sendError(String userId, String errorMessage) {
        sendToUser(userId, WSMessage.error(errorMessage));
    }

    // 内部数据类
    public record InviteData(String inviterId, String inviterName, String roomName, String mode, String title, String inviteId) {}
    public record AcceptData(String userId, String roomName) {}
    public record LeaveData(String userId, String roomName) {}
}
