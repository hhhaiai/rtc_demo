package com.phoenix.rtc.service;

import io.livekit.server.LiveKitServerClient;
import io.livekit.server.ParticipantInfo;
import io.livekit.server.RoomInfo;
import io.livekit.server.TokenOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * LiveKit 服务
 * 负责 Token 生成、房间管理等
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LiveKitService {

    private final LiveKitServerClient liveKitClient;

    @Value("${livekit.url:ws://localhost:7880}")
    private String liveKitUrl;

    @Value("${livekit.api.key:devkey}")
    private String apiKey;

    @Value("${livekit.api.secret:secret}")
    private String apiSecret;

    /**
     * 生成加入房间的 Token
     *
     * @param userId 用户 ID
     * @param roomName 房间名称
     * @param role 角色 (host/publisher/subscriber)
     * @param metadata 元数据 (可选)
     * @return JWT Token
     */
    public String generateToken(String userId, String roomName, String role, String metadata) {
        try {
            // Token 有效期设置为 2 小时
            long expirationTime = System.currentTimeMillis() + (2 * 60 * 60 * 1000);

            TokenOptions options = TokenOptions.builder()
                    .setRoomJoin(true)
                    .setRoomName(roomName)
                    .setIdentity(userId)
                    .setMetadata(metadata != null ? metadata : "")
                    .setGrants(true, true) // 允许发布/订阅
                    .setExpiration(expirationTime)
                    .build();

            // 根据角色设置权限
            if ("host".equals(role)) {
                options.setGrants(true, true); // 可推流可拉流
            } else if ("subscriber".equals(role)) {
                options.setGrants(false, true); // 只能拉流
            } else {
                options.setGrants(true, true); // 默认可推可拉
            }

            String token = liveKitClient.createToken(apiKey, apiSecret, options);
            log.info("生成 Token - 用户: {}, 房间: {}, 角色: {}, 过期时间: {}",
                userId, roomName, role, expirationTime);
            return token;
        } catch (Exception e) {
            log.error("生成 Token 失败 - 用户: {}, 房间: {}", userId, roomName, e);
            throw new RuntimeException("Token 生成失败: " + e.getMessage());
        }
    }

    /**
     * 创建房间
     *
     * @param roomName 房间名称
     * @param emptyTimeout 空房间超时时间(秒)
     * @param maxParticipants 最大参与人数
     * @return 房间信息
     */
    public RoomInfo createRoom(String roomName, Integer emptyTimeout, Integer maxParticipants) {
        try {
            io.livekit.server.CreateRoomRequest request = new io.livekit.server.CreateRoomRequest();
            request.setName(roomName);
            if (emptyTimeout != null) {
                request.setEmptyTimeout(emptyTimeout);
            }
            if (maxParticipants != null) {
                request.setMaxParticipants(maxParticipants);
            }

            RoomInfo roomInfo = liveKitClient.createRoom(request);
            log.info("创建房间成功 - 房间: {}", roomName);
            return roomInfo;
        } catch (Exception e) {
            log.error("创建房间失败", e);
            throw new RuntimeException("创建房间失败: " + e.getMessage());
        }
    }

    /**
     * 获取房间信息
     *
     * @param roomName 房间名称
     * @return 房间信息
     */
    public RoomInfo getRoomInfo(String roomName) {
        try {
            return liveKitClient.getRoom(roomName);
        } catch (Exception e) {
            log.error("获取房间信息失败: {}", roomName, e);
            return null;
        }
    }

    /**
     * 列出房间内的参与者
     *
     * @param roomName 房间名称
     * @return 参与者列表
     */
    public List<ParticipantInfo> listParticipants(String roomName) {
        try {
            return liveKitClient.listParticipants(roomName);
        } catch (Exception e) {
            log.error("列出参与者失败: {}", roomName, e);
            return List.of();
        }
    }

    /**
     * 删除房间
     *
     * @param roomName 房间名称
     */
    public void deleteRoom(String roomName) {
        try {
            liveKitClient.deleteRoom(roomName);
            log.info("删除房间成功 - 房间: {}", roomName);
        } catch (Exception e) {
            log.error("删除房间失败: {}", roomName, e);
        }
    }

    /**
     * 将参与者踢出房间
     *
     * @param roomName 房间名称
     * @param participantIdentity 参与者身份
     */
    public void removeParticipant(String roomName, String participantIdentity) {
        try {
            liveKitClient.removeParticipant(roomName, participantIdentity);
            log.info("移除参与者成功 - 房间: {}, 用户: {}", roomName, participantIdentity);
        } catch (Exception e) {
            log.error("移除参与者失败: {}", roomName, e);
        }
    }

    /**
     * 生成唯一的房间名称
     *
     * @param prefix 前缀
     * @return 房间名称
     */
    public String generateRoomName(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 获取 LiveKit 服务器 URL
     */
    public String getLiveKitUrl() {
        return liveKitUrl;
    }
}
