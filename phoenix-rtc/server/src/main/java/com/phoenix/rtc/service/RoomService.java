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
 * 房间管理服务
 * 负责房间创建、加入、离开等业务逻辑
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoomService {

    private final MediaAdapter mediaAdapter;  // 使用抽象接口
    private final RtcSessionRepository sessionRepository;
    private final RtcParticipantRepository participantRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MetricsConfig metricsConfig;
    private final Timer callCreationTimer;
    private final Timer tokenGenerationTimer;

    // Redis Key 模式 (参考 n.md 完善设计)
    private static final String ROOM_META_KEY = "rtc:room:%s:meta";           // 房间元数据 (Hash)
    private static final String ROOM_MEMBERS_KEY = "rtc:room:%s:members";     // 成员列表 (Set)
    private static final String ROOM_MEMBER_KEY = "rtc:room:%s:member:%s";    // 成员详情 (Hash)
    private static final String SESSION_KEY = "rtc:session:%s";              // 用户会话映射 (String)
    private static final String INVITE_KEY = "rtc:invite:%s";                // 邀请缓存 (Hash)
    private static final String RATELIMIT_KEY = "ratelimit:rtc:%s";          // 限流计数器 (String)

    /**
     * 发起通话 - 创建房间并生成 Token
     * 使用 MediaAdapter 接口，支持未来扩展多种媒体服务器
     *
     * 修复: 将外部 RPC 调用 (mediaAdapter.createRoom) 移出 @Transactional 范围
     * 避免长时间占用数据库连接池
     */
    public TokenResponse startCall(CallRequest request, String currentUserId) {
        return callCreationTimer.record(() -> {
            // 1. 生成房间名称
            String roomName = generateRoomName("room");

            // 2. 确定房间类型
            int sessionType = parseSessionType(request.getSessionType());
            String roomTypeStr = request.getSessionType().toLowerCase();

            // 3. 配置房间参数
            Integer maxParticipants = request.getMaxParticipants();
            if (maxParticipants == null) {
                maxParticipants = sessionType == 3 ? 1000 : 10; // 直播1000人，其他10人
            }

            RoomConfig config = RoomConfig.builder()
                    .emptyTimeout(300)
                    .maxParticipants(maxParticipants)
                    .roomType(roomTypeStr)
                    .recordingEnabled(false)
                    .build();

            // 4. 先调用外部 RPC (LiveKit) - 不在事务内
            // 如果这一步失败，不会影响数据库状态
            RoomInfo roomInfo;
            try {
                roomInfo = mediaAdapter.createRoom(roomName, config);
            } catch (Exception e) {
                log.error("创建 LiveKit 房间失败，不执行数据库操作", e);
                throw new RuntimeException("媒体服务器创建房间失败: " + e.getMessage());
            }

            // 5. 开始数据库事务
            try {
                return createRoomInTransaction(request, currentUserId, roomName, roomTypeStr,
                                               maxParticipants, sessionType, roomInfo);
            } catch (Exception e) {
                // 如果数据库操作失败，尝试清理 LiveKit 房间
                try {
                    mediaAdapter.deleteRoom(roomName);
                    log.warn("数据库事务失败，已清理 LiveKit 房间: {}", roomName);
                } catch (Exception cleanupException) {
                    log.error("清理 LiveKit 房间失败，请手动清理: {}", roomName, cleanupException);
                }
                throw e;
            }
        });
    }

    /**
     * 在事务内创建房间记录
     * 分离事务逻辑和外部调用逻辑
     */
    @Transactional
    private TokenResponse createRoomInTransaction(CallRequest request, String currentUserId,
                                                   String roomName, String roomTypeStr,
                                                   int maxParticipants, int sessionType,
                                                   RoomInfo roomInfo) {
        try {
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

            RtcSession savedSession = sessionRepository.save(session);

            // 6. 添加发起人到参与者列表
            RtcParticipant participant = RtcParticipant.builder()
                    .sessionId(savedSession.getId())
                    .userId(currentUserId)
                    .joinTime(LocalDateTime.now())
                    .role("host")
                    .build();
            participantRepository.save(participant);

            // 7. 使用 MediaAdapter 生成 Token
            String token = tokenGenerationTimer.record(() ->
                mediaAdapter.generateToken(currentUserId, roomName, "host")
            );

            // 8. 完善 Redis 数据结构
            // 8.1 房间元数据 (Hash)
            String roomMetaKey = String.format(ROOM_META_KEY, roomName);
            redisTemplate.opsForHash().put(roomMetaKey, "sessionId", savedSession.getId());
            redisTemplate.opsForHash().put(roomMetaKey, "initiatorId", currentUserId);
            redisTemplate.opsForHash().put(roomMetaKey, "roomType", roomTypeStr);
            redisTemplate.opsForHash().put(roomMetaKey, "status", "active");
            redisTemplate.opsForHash().put(roomMetaKey, "maxMembers", maxParticipants);
            redisTemplate.opsForHash().put(roomMetaKey, "title", request.getTitle());
            redisTemplate.opsForHash().put(roomMetaKey, "createdAt", System.currentTimeMillis());
            redisTemplate.expire(roomMetaKey, 2, TimeUnit.HOURS);

            // 8.2 成员列表 (Set) - 添加发起人
            String roomMembersKey = String.format(ROOM_MEMBERS_KEY, roomName);
            redisTemplate.opsForSet().add(roomMembersKey, currentUserId);
            redisTemplate.expire(roomMembersKey, 2, TimeUnit.HOURS);

            // 8.3 发起人详情 (Hash)
            String memberKey = String.format(ROOM_MEMBER_KEY, roomName, currentUserId);
            redisTemplate.opsForHash().put(memberKey, "role", "host");
            redisTemplate.opsForHash().put(memberKey, "joinedAt", System.currentTimeMillis());
            redisTemplate.opsForHash().put(memberKey, "audioEnabled", true);
            redisTemplate.opsForHash().put(memberKey, "videoEnabled", true);
            redisTemplate.expire(memberKey, 2, TimeUnit.HOURS);

            // 8.4 用户会话映射 (String)
            String sessionKey = String.format(SESSION_KEY, currentUserId);
            redisTemplate.opsForValue().set(sessionKey, roomName, 2, TimeUnit.HOURS);

            // 9. 更新监控指标
            metricsConfig.incrementTotalCalls();
            metricsConfig.incrementActiveCalls();

            log.info("发起通话成功 - 用户: {}, 房间: {}, 类型: {}, Redis结构已完善",
                currentUserId, roomName, request.getSessionType());

            // 获取 LiveKit URL (从适配器)
            String liveKitUrl = (mediaAdapter instanceof com.phoenix.rtc.adapter.LiveKitAdapter) ?
                ((com.phoenix.rtc.adapter.LiveKitAdapter) mediaAdapter).getLiveKitUrl() : "ws://localhost:7880";

            return TokenResponse.builder()
                    .url(liveKitUrl)
                    .token(token)
                    .roomName(roomName)
                    .roomTitle(request.getTitle())
                    .expiresAt(System.currentTimeMillis() / 1000 + 3600)
                    .build();
        } catch (Exception e) {
            // 记录失败指标
            metricsConfig.incrementFailedCalls();
            log.error("发起通话失败", e);
            throw e;
        }
    }

    /**
     * 生成唯一的房间名称
     */
    private String generateRoomName(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 加入通话 - 生成加入房间的 Token
     * 使用 MediaAdapter 接口
     *
     * 修复: 将 Token 生成移出 @Transactional 范围
     */
    public TokenResponse joinCall(String roomName, String currentUserId) {
        // 1. 查询房间信息 (只读操作，不需要事务)
        RtcSession session = sessionRepository.findByRoomName(roomName)
                .orElseThrow(() -> new RuntimeException("房间不存在: " + roomName));

        // 2. 检查房间状态
        if (!session.getStatus().equals(RtcSession.Status.ACTIVE.getCode())) {
            throw new RuntimeException("房间已结束或异常");
        }

        // 3. 在事务内处理参与者记录
        boolean isNewParticipant = joinRoomInTransaction(session, currentUserId);

        if (!isNewParticipant) {
            log.warn("用户已加入房间，重新生成 Token - 用户: {}, 房间: {}", currentUserId, roomName);
        }

        // 4. 生成 Token (外部调用，不在事务内)
        String token;
        try {
            token = mediaAdapter.generateToken(currentUserId, roomName, "publisher");
        } catch (Exception e) {
            log.error("生成 LiveKit Token 失败", e);
            throw new RuntimeException("生成 Token 失败: " + e.getMessage());
        }

        // 5. 更新 Redis (不在事务内，但幂等)
        updateRedisOnJoin(roomName, currentUserId);

        log.info("加入通话成功 - 用户: {}, 房间: {}", currentUserId, roomName);

        // 6. 获取 LiveKit URL
        String liveKitUrl = (mediaAdapter instanceof com.phoenix.rtc.adapter.LiveKitAdapter) ?
            ((com.phoenix.rtc.adapter.LiveKitAdapter) mediaAdapter).getLiveKitUrl() : "ws://localhost:7880";

        return TokenResponse.builder()
                .url(liveKitUrl)
                .token(token)
                .roomName(roomName)
                .roomTitle(session.getRoomTitle())
                .expiresAt(System.currentTimeMillis() / 1000 + 3600)
                .build();
    }

    /**
     * 在事务内处理参与者加入
     * 返回是否为新参与者
     */
    @Transactional
    private boolean joinRoomInTransaction(RtcSession session, String currentUserId) {
        // 检查是否已加入
        boolean alreadyJoined = participantRepository
                .findBySessionIdAndUserId(session.getId(), currentUserId)
                .isPresent();

        if (!alreadyJoined) {
            // 添加到参与者列表
            RtcParticipant participant = RtcParticipant.builder()
                    .sessionId(session.getId())
                    .userId(currentUserId)
                    .joinTime(LocalDateTime.now())
                    .role("publisher")
                    .build();
            participantRepository.save(participant);
            return true;
        }
        return false;
    }

    /**
     * 更新 Redis 数据结构 (幂等操作)
     */
    private void updateRedisOnJoin(String roomName, String currentUserId) {
        try {
            // 6.1 更新成员列表 (Set)
            String roomMembersKey = String.format(ROOM_MEMBERS_KEY, roomName);
            redisTemplate.opsForSet().add(roomMembersKey, currentUserId);
            redisTemplate.expire(roomMembersKey, 2, TimeUnit.HOURS);

            // 6.2 添加成员详情 (Hash)
            String memberKey = String.format(ROOM_MEMBER_KEY, roomName, currentUserId);
            redisTemplate.opsForHash().put(memberKey, "role", "publisher");
            redisTemplate.opsForHash().put(memberKey, "joinedAt", System.currentTimeMillis());
            redisTemplate.opsForHash().put(memberKey, "audioEnabled", true);
            redisTemplate.opsForHash().put(memberKey, "videoEnabled", true);
            redisTemplate.expire(memberKey, 2, TimeUnit.HOURS);

            // 6.3 用户会话映射 (String)
            String sessionKey = String.format(SESSION_KEY, currentUserId);
            redisTemplate.opsForValue().set(sessionKey, roomName, 2, TimeUnit.HOURS);

            // 6.4 更新房间元数据中的成员计数
            String roomMetaKey = String.format(ROOM_META_KEY, roomName);
            Long currentCount = redisTemplate.opsForHash().increment(roomMetaKey, "currentMembers", 1);
            redisTemplate.expire(roomMetaKey, 2, TimeUnit.HOURS);

            log.debug("Redis 更新完成 - 房间: {}, 当前成员: {}", roomName, currentCount);
        } catch (Exception e) {
            log.warn("Redis 更新失败，但不影响主流程: {}", e.getMessage());
            // 不抛出异常，因为主要业务（数据库和Token）已完成
        }
    }

    /**
     * 离开通话
     * 使用 MediaAdapter 接口
     *
     * 修复: 将外部 RPC 调用 (mediaAdapter.deleteRoom) 移出 @Transactional 范围
     */
    public void leaveCall(String roomName, String currentUserId) {
        // 1. 在事务内处理数据库更新和 Redis 清理
        LeaveCallResult result = leaveRoomInTransaction(roomName, currentUserId);

        // 2. 如果房间已空，异步删除 LiveKit 房间
        if (result.isRoomEmpty) {
            try {
                mediaAdapter.deleteRoom(roomName);
                log.info("已删除 LiveKit 房间: {}", roomName);
            } catch (Exception e) {
                // 记录错误但不抛出，因为主业务已完成
                log.error("删除 LiveKit 房间失败，请手动清理: {}", roomName, e);
            }
        }
    }

    /**
     * 离开房间的事务操作
     * 返回房间是否已空的信息
     */
    @Transactional
    private LeaveCallResult leaveRoomInTransaction(String roomName, String currentUserId) {
        // 1. 查询会话
        RtcSession session = sessionRepository.findByRoomName(roomName)
                .orElseThrow(() -> new RuntimeException("房间不存在"));

        // 2. 更新参与者离开时间
        participantRepository.findBySessionIdAndUserId(session.getId(), currentUserId)
                .ifPresent(participant -> {
                    LocalDateTime now = LocalDateTime.now();
                    Integer duration = (int) (java.time.Duration.between(participant.getJoinTime(), now).getSeconds());

                    participantRepository.updateLeaveTime(participant.getId(), now, duration);
                });

        // 3. 检查房间是否还有人
        Integer onlineCount = participantRepository.countOnlineParticipants(roomName);

        // 4. 如果房间为空，结束会话
        if (onlineCount == 0 || onlineCount == null) {
            session.setEndTime(LocalDateTime.now());
            session.setStatus(RtcSession.Status.ENDED.getCode());
            sessionRepository.save(session);

            // 5. 清理所有 Redis 缓存
            String roomMetaKey = String.format(ROOM_META_KEY, roomName);
            String roomMembersKey = String.format(ROOM_MEMBERS_KEY, roomName);
            String memberKey = String.format(ROOM_MEMBER_KEY, roomName, currentUserId);
            String sessionKey = String.format(SESSION_KEY, currentUserId);

            redisTemplate.delete(roomMetaKey);
            redisTemplate.delete(roomMembersKey);
            redisTemplate.delete(memberKey);
            redisTemplate.delete(sessionKey);

            // 6. 更新监控指标 - 减少活跃通话数
            metricsConfig.decrementActiveCalls();

            log.info("房间已空，结束会话并清理Redis - 房间: {}", roomName);
            return new LeaveCallResult(true, 0);
        } else {
            // 6. 房间还有人，只清理当前用户的缓存
            // 6.1 从成员列表移除
            String roomMembersKey = String.format(ROOM_MEMBERS_KEY, roomName);
            redisTemplate.opsForSet().remove(roomMembersKey, currentUserId);

            // 6.2 删除成员详情
            String memberKey = String.format(ROOM_MEMBER_KEY, roomName, currentUserId);
            redisTemplate.delete(memberKey);

            // 6.3 删除用户会话映射
            String sessionKey = String.format(SESSION_KEY, currentUserId);
            redisTemplate.delete(sessionKey);

            // 6.4 更新房间成员计数
            String roomMetaKey = String.format(ROOM_META_KEY, roomName);
            redisTemplate.opsForHash().increment(roomMetaKey, "currentMembers", -1);

            log.info("用户离开通话 - 用户: {}, 房间: {}, 剩余成员: {}", currentUserId, roomName, onlineCount - 1);
            return new LeaveCallResult(false, onlineCount - 1);
        }
    }

    /**
     * 离开通话的结果
     */
    private static class LeaveCallResult {
        final boolean isRoomEmpty;
        final int remainingMembers;

        LeaveCallResult(boolean isRoomEmpty, int remainingMembers) {
            this.isRoomEmpty = isRoomEmpty;
            this.remainingMembers = remainingMembers;
        }
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
            case "video" -> RtcSession.SessionType.ONE_V_ONE.getCode();
            case "audio" -> RtcSession.SessionType.ONE_V_ONE.getCode();
            case "live" -> RtcSession.SessionType.LIVE.getCode();
            case "group" -> RtcSession.SessionType.GROUP.getCode();
            default -> RtcSession.SessionType.ONE_V_ONE.getCode();
        };
    }
}
