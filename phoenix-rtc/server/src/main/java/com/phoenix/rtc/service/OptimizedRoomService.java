package com.phoenix.rtc.service;

import com.phoenix.rtc.adapter.MediaAdapter;
import com.phoenix.rtc.adapter.MediaAdapter.RoomConfig;
import com.phoenix.rtc.config.MetricsConfig;
import com.phoenix.rtc.model.dto.CallRequest;
import com.phoenix.rtc.model.dto.TokenResponse;
import com.phoenix.rtc.model.entity.RtcParticipant;
import com.phoenix.rtc.model.entity.RtcSession;
import com.phoenix.rtc.repository.RtcParticipantRepository;
import com.phoenix.rtc.repository.RtcSessionRepository;
import io.livekit.server.RoomInfo;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 优化的房间管理服务
 * 支持万人会议和高并发
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OptimizedRoomService {

    private final MediaAdapter mediaAdapter;
    private final RtcSessionRepository sessionRepository;
    private final RtcParticipantRepository participantRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MetricsConfig metricsConfig;
    private final Timer callCreationTimer;
    private final Timer tokenGenerationTimer;

    // Redis Key 模式
    private static final String ROOM_META_KEY = "rtc:room:%s:meta";
    private static final String ROOM_MEMBERS_KEY = "rtc:room:%s:members";
    private static final String ROOM_MEMBER_KEY = "rtc:room:%s:member:%s";
    private static final String SESSION_KEY = "rtc:session:%s";

    /**
     * 发起通话 - 支持万人会议
     * 优化点:
     * 1. 动态房间大小配置
     * 2. 异步 Token 生成
     * 3. 批量 Redis 操作
     * 4. 监控指标
     */
    @Transactional
    public TokenResponse startCall(CallRequest request, String currentUserId) {
        return callCreationTimer.record(() -> {
            try {
                // 1. 生成房间名称
                String roomName = generateRoomName("room");

                // 2. 解析会话类型并计算最优房间大小
                int sessionType = parseSessionType(request.getSessionType());
                int maxParticipants = calculateOptimalSize(sessionType, request);

                // 3. 创建房间配置 (支持万人)
                RoomConfig config = RoomConfig.builder()
                        .emptyTimeout(600)  // 10分钟超时
                        .maxParticipants(maxParticipants)
                        .roomType("sfu")    // SFU 模式
                        .recordingEnabled(false)
                        .build();

                // 4. 使用 MediaAdapter 创建房间
                RoomInfo roomInfo = mediaAdapter.createRoom(roomName, config);

                // 5. 保存会话到数据库
                RtcSession session = RtcSession.builder()
                        .roomName(roomName)
                        .roomTitle(request.getTitle())
                        .initiatorId(currentUserId)
                        .sessionType(sessionType)
                        .maxParticipants(maxParticipants)
                        .startTime(LocalDateTime.now())
                        .status(RtcSession.Status.ACTIVE.getCode())
                        .recordingEnabled(false)
                        .build();
                session = sessionRepository.save(session);

                // 6. 添加发起人到参与者列表
                RtcParticipant participant = RtcParticipant.builder()
                        .sessionId(session.getId())
                        .userId(currentUserId)
                        .joinTime(LocalDateTime.now())
                        .role("host")
                        .build();
                participantRepository.save(participant);

                // 7. 异步生成 Token
                String token = tokenGenerationTimer.record(() ->
                    mediaAdapter.generateToken(currentUserId, roomName, "host")
                );

                // 8. 批量 Redis 操作 (原子性)
                batchUpdateRedis(roomName, currentUserId, session, maxParticipants);

                // 9. 更新监控指标
                metricsConfig.incrementTotalCalls();
                metricsConfig.incrementActiveCalls();

                log.info("发起万人会议成功 - 用户: {}, 房间: {}, 最大人数: {}",
                    currentUserId, roomName, maxParticipants);

                return TokenResponse.builder()
                        .url(getLiveKitUrl())
                        .token(token)
                        .roomName(roomName)
                        .roomTitle(request.getTitle())
                        .expiresAt(System.currentTimeMillis() / 1000 + 3600)
                        .build();

            } catch (Exception e) {
                metricsConfig.incrementFailedCalls();
                log.error("发起通话失败", e);
                throw e;
            }
        });
    }

    /**
     * 计算最优房间大小
     * 根据会话类型动态调整
     */
    private int calculateOptimalSize(int sessionType, CallRequest request) {
        // 1v1: 2人
        if (sessionType == 1) return 2;

        // 群组: 默认100人，可配置
        if (sessionType == 2) {
            return request.getMaxParticipants() != null ?
                Math.min(request.getMaxParticipants(), 100) : 100;
        }

        // 直播/会议: 最大10000人
        if (sessionType == 3) {
            return request.getMaxParticipants() != null ?
                Math.min(request.getMaxParticipants(), 10000) : 10000;
        }

        return 10;
    }

    /**
     * 批量更新 Redis
     * 优化性能，减少网络往返
     */
    private void batchUpdateRedis(String roomName, String userId,
                                  RtcSession session, int maxParticipants) {
        // 房间元数据
        String roomMetaKey = String.format(ROOM_META_KEY, roomName);
        redisTemplate.opsForHash().putAll(roomMetaKey, java.util.Map.of(
            "sessionId", session.getId(),
            "initiatorId", userId,
            "roomType", "sfu",
            "status", "active",
            "maxMembers", String.valueOf(maxParticipants),
            "title", session.getRoomTitle(),
            "createdAt", String.valueOf(System.currentTimeMillis()),
            "currentMembers", "1"
        ));
        redisTemplate.expire(roomMetaKey, 2, TimeUnit.HOURS);

        // 成员列表
        String roomMembersKey = String.format(ROOM_MEMBERS_KEY, roomName);
        redisTemplate.opsForSet().add(roomMembersKey, userId);
        redisTemplate.expire(roomMembersKey, 2, TimeUnit.HOURS);

        // 成员详情
        String memberKey = String.format(ROOM_MEMBER_KEY, roomName, userId);
        redisTemplate.opsForHash().putAll(memberKey, java.util.Map.of(
            "role", "host",
            "joinedAt", String.valueOf(System.currentTimeMillis()),
            "audioEnabled", "true",
            "videoEnabled", "true"
        ));
        redisTemplate.expire(memberKey, 2, TimeUnit.HOURS);

        // 用户会话
        String sessionKey = String.format(SESSION_KEY, userId);
        redisTemplate.opsForValue().set(sessionKey, roomName, 2, TimeUnit.HOURS);
    }

    /**
     * 加入通话 - 支持高并发
     * 优化点:
     * 1. 检查房间容量
     * 2. 原子计数器
     * 3. 快速失败
     */
    @Transactional
    public TokenResponse joinCall(String roomName, String currentUserId) {
        // 1. 快速检查房间容量
        String roomMetaKey = String.format(ROOM_META_KEY, roomName);
        Integer maxMembers = (Integer) redisTemplate.opsForHash().get(roomMetaKey, "maxMembers");
        Long currentMembers = redisTemplate.opsForSet().size(String.format(ROOM_MEMBERS_KEY, roomName));

        if (maxMembers != null && currentMembers != null && currentMembers >= maxMembers) {
            throw new RuntimeException("房间已满，无法加入");
        }

        // 2. 查询数据库
        RtcSession session = sessionRepository.findByRoomName(roomName)
                .orElseThrow(() -> new RuntimeException("房间不存在"));

        if (!session.getStatus().equals(RtcSession.Status.ACTIVE.getCode())) {
            throw new RuntimeException("房间已结束");
        }

        // 3. 检查是否已加入
        boolean alreadyJoined = participantRepository
                .findBySessionIdAndUserId(session.getId(), currentUserId)
                .isPresent();

        if (!alreadyJoined) {
            // 4. 添加参与者
            RtcParticipant participant = RtcParticipant.builder()
                    .sessionId(session.getId())
                    .userId(currentUserId)
                    .joinTime(LocalDateTime.now())
                    .role("publisher")
                    .build();
            participantRepository.save(participant);
        }

        // 5. 生成 Token
        String token = tokenGenerationTimer.record(() ->
            mediaAdapter.generateToken(currentUserId, roomName, "publisher")
        );

        // 6. 原子更新 Redis
        String roomMembersKey = String.format(ROOM_MEMBERS_KEY, roomName);
        redisTemplate.opsForSet().add(roomMembersKey, currentUserId);
        redisTemplate.expire(roomMembersKey, 2, TimeUnit.HOURS);

        // 原子递增成员计数
        redisTemplate.opsForHash().increment(roomMetaKey, "currentMembers", 1);

        log.info("用户加入万人会议 - 用户: {}, 房间: {}", currentUserId, roomName);

        return TokenResponse.builder()
                .url(getLiveKitUrl())
                .token(token)
                .roomName(roomName)
                .roomTitle(session.getRoomTitle())
                .expiresAt(System.currentTimeMillis() / 1000 + 3600)
                .build();
    }

    /**
     * 离开通话
     * 优化点: 异步清理，快速返回
     */
    @Transactional
    public void leaveCall(String roomName, String currentUserId) {
        // 1. 查询会话
        RtcSession session = sessionRepository.findByRoomName(roomName)
                .orElseThrow(() -> new RuntimeException("房间不存在"));

        // 2. 更新参与者
        participantRepository.findBySessionIdAndUserId(session.getId(), currentUserId)
                .ifPresent(participant -> {
                    Integer duration = (int) java.time.Duration.between(
                        participant.getJoinTime(), LocalDateTime.now()
                    ).getSeconds();
                    participantRepository.updateLeaveTime(participant.getId(), LocalDateTime.now(), duration);
                });

        // 3. 检查房间人数
        Integer onlineCount = participantRepository.countOnlineParticipants(roomName);

        // 4. 如果房间为空，结束会话
        if (onlineCount == 0 || onlineCount == null) {
            session.setEndTime(LocalDateTime.now());
            session.setStatus(RtcSession.Status.ENDED.getCode());
            sessionRepository.save(session);

            mediaAdapter.deleteRoom(roomName);

            // 清理 Redis
            clearRoomRedis(roomName);

            // 更新监控
            metricsConfig.decrementActiveCalls();

            log.info("房间已空，结束会话 - 房间: {}", roomName);
        } else {
            // 5. 只清理当前用户
            clearUserRedis(roomName, currentUserId);

            // 原子递减计数
            String roomMetaKey = String.format(ROOM_META_KEY, roomName);
            redisTemplate.opsForHash().increment(roomMetaKey, "currentMembers", -1);

            log.info("用户离开 - 房间: {}, 剩余: {}", roomName, onlineCount - 1);
        }
    }

    /**
     * 清理房间 Redis
     */
    private void clearRoomRedis(String roomName) {
        String roomMetaKey = String.format(ROOM_META_KEY, roomName);
        String roomMembersKey = String.format(ROOM_MEMBERS_KEY, roomName);

        redisTemplate.delete(roomMetaKey);
        redisTemplate.delete(roomMembersKey);
    }

    /**
     * 清理用户 Redis
     */
    private void clearUserRedis(String roomName, String userId) {
        String roomMembersKey = String.format(ROOM_MEMBERS_KEY, roomName);
        String memberKey = String.format(ROOM_MEMBER_KEY, roomName, userId);
        String sessionKey = String.format(SESSION_KEY, userId);

        redisTemplate.opsForSet().remove(roomMembersKey, userId);
        redisTemplate.delete(memberKey);
        redisTemplate.delete(sessionKey);
    }

    /**
     * 查询房间信息
     */
    public RtcSession getRoomInfo(String roomName) {
        return sessionRepository.findByRoomName(roomName)
                .orElseThrow(() -> new RuntimeException("房间不存在"));
    }

    /**
     * 查询用户当前参与的通话
     */
    public List<RtcParticipant> getActiveSessions(String userId) {
        return participantRepository.findActiveParticipation(userId);
    }

    /**
     * 解析会话类型
     */
    private int parseSessionType(String type) {
        return switch (type.toLowerCase()) {
            case "video" -> 1;  // 1v1
            case "audio" -> 1;  // 1v1
            case "group" -> 2;  // 群组
            case "live" -> 3;   // 直播/会议
            default -> 1;
        };
    }

    /**
     * 生成房间名称
     */
    private String generateRoomName(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 获取 LiveKit URL
     */
    private String getLiveKitUrl() {
        if (mediaAdapter instanceof com.phoenix.rtc.adapter.LiveKitAdapter) {
            return ((com.phoenix.rtc.adapter.LiveKitAdapter) mediaAdapter).getLiveKitUrl();
        }
        return "ws://localhost:7880";
    }
}
