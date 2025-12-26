package com.phoenix.rtc.adapter;

import io.livekit.server.CreateRoomRequest;
import io.livekit.server.LiveKitServerClient;
import io.livekit.server.RoomInfo;
import io.livekit.server.TokenOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.function.Supplier;

/**
 * LiveKit 媒体服务器适配器实现
 * 实现 MediaAdapter 接口，封装 LiveKit SDK 调用
 *
 * 修复说明:
 * 1. 增加重试机制，处理短暂的网络波动
 * 2. 所有外部调用都添加重试逻辑
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LiveKitAdapter implements MediaAdapter {

    private final LiveKitServerClient liveKitClient;

    @Value("${LIVEKIT_URL}")
    private String liveKitUrl;

    @Value("${LIVEKIT_API_KEY}")
    private String apiKey;

    @Value("${LIVEKIT_API_SECRET}")
    private String apiSecret;

    // 重试配置
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 500; // 500ms 延迟

    /**
     * 启动时检查 LiveKit 配置
     */
    @PostConstruct
    public void validateConfig() {
        if (liveKitUrl == null || liveKitUrl.trim().isEmpty()) {
            throw new IllegalStateException("LIVEKIT_URL 环境变量未配置，系统无法启动！");
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("LIVEKIT_API_KEY 环境变量未配置，系统无法启动！");
        }
        if (apiSecret == null || apiSecret.trim().isEmpty()) {
            throw new IllegalStateException("LIVEKIT_API_SECRET 环境变量未配置，系统无法启动！");
        }
        log.info("LiveKit 配置验证通过 - URL: {}", liveKitUrl);
    }

    @Override
    public RoomInfo createRoom(String name, RoomConfig config) {
        return executeWithRetry(() -> {
            CreateRoomRequest request = new CreateRoomRequest();
            request.setName(name);

            if (config.getEmptyTimeout() != null) {
                request.setEmptyTimeout(config.getEmptyTimeout());
            }

            if (config.getMaxParticipants() != null) {
                request.setMaxParticipants(config.getMaxParticipants());
            }

            // LiveKit 原生支持元数据，可以存储 roomType 等信息
            if (config.getRoomType() != null || config.getRecordingEnabled() != null) {
                StringBuilder metadata = new StringBuilder();
                if (config.getRoomType() != null) {
                    metadata.append("roomType:").append(config.getRoomType());
                }
                if (config.getRecordingEnabled() != null) {
                    if (metadata.length() > 0) metadata.append(",");
                    metadata.append("recording:").append(config.getRecordingEnabled());
                }
                request.setMetadata(metadata.toString());
            }

            RoomInfo roomInfo = liveKitClient.createRoom(request);
            log.info("LiveKit 创建房间成功 - 名称: {}, 类型: {}", name, config.getRoomType());
            return roomInfo;
        }, "createRoom", name);
    }

    @Override
    public String generateToken(String userId, String roomName, String role) {
        return executeWithRetry(() -> {
            long expirationTime = System.currentTimeMillis() + (2 * 60 * 60 * 1000); // 2小时

            TokenOptions options = TokenOptions.builder()
                    .setRoomJoin(true)
                    .setRoomName(roomName)
                    .setIdentity(userId)
                    .setGrants(true, true)
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
            log.debug("生成 LiveKit Token - 用户: {}, 房间: {}, 角色: {}", userId, roomName, role);
            return token;
        }, "generateToken", roomName + ":" + userId);
    }

    @Override
    public void deleteRoom(String roomName) {
        try {
            // 删除操作不重试，失败仅记录日志
            liveKitClient.deleteRoom(roomName);
            log.info("删除房间成功 - 房间: {}", roomName);
        } catch (Exception e) {
            log.error("删除房间失败: {}，已忽略", roomName, e);
            // 不抛出异常，避免影响主流程
        }
    }

    @Override
    public RoomInfo getRoomInfo(String roomName) {
        try {
            return executeWithRetry(() -> liveKitClient.getRoom(roomName), "getRoomInfo", roomName);
        } catch (Exception e) {
            log.error("获取房间信息失败: {}", roomName, e);
            return null;
        }
    }

    /**
     * 获取 LiveKit 服务器 URL
     */
    public String getLiveKitUrl() {
        return liveKitUrl;
    }

    /**
     * 执行带重试的操作
     *
     * @param supplier 业务逻辑
     * @param operation 操作名称（用于日志）
     * @param context 上下文信息（用于日志）
     * @param <T> 返回类型
     * @return 执行结果
     */
    private <T> T executeWithRetry(Supplier<T> supplier, String operation, String context) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return supplier.get();
            } catch (Exception e) {
                lastException = e;
                log.warn("LiveKit {} 操作失败 (尝试 {}/{}): {} - {}",
                        operation, attempt, MAX_RETRIES, context, e.getMessage());

                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt); // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("操作被中断", ie);
                    }
                }
            }
        }

        // 所有重试都失败
        throw new RuntimeException(operation + " 失败 (重试 " + MAX_RETRIES + " 次): " + lastException.getMessage(), lastException);
    }
}
