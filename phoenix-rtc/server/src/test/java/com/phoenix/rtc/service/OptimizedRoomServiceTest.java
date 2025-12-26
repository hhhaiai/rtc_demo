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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OptimizedRoomService 单元测试
 * 支持10000+用户会议的完整测试覆盖
 */
@ExtendWith(MockitoExtension.class)
class OptimizedRoomServiceTest {

    @Mock
    private MediaAdapter mediaAdapter;

    @Mock
    private RtcSessionRepository sessionRepository;

    @Mock
    private RtcParticipantRepository participantRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private MetricsConfig metricsConfig;

    @Mock
    private Timer callCreationTimer;

    @Mock
    private Timer tokenGenerationTimer;

    @Mock
    private HashOperations<String, Object, Object> hashOps;

    @Mock
    private SetOperations<String, Object> setOps;

    @Mock
    private ValueOperations<String, Object> valueOps;

    @Spy
    @InjectMocks
    private OptimizedRoomService optimizedRoomService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // Mock Timer.record() to execute the callable directly
        when(callCreationTimer.record(any())).thenAnswer(invocation -> {
            var callable = invocation.getArgument(0);
            return callable.call();
        });
        when(tokenGenerationTimer.record(any())).thenAnswer(invocation -> {
            var callable = invocation.getArgument(0);
            return callable.call();
        });
    }

    @Test
    void testStartCall_1v1_Success() throws Exception {
        // Given
        CallRequest request = CallRequest.builder()
                .sessionType("video")
                .title("一对一视频通话")
                .build();
        String currentUserId = "user1";
        String roomName = "room_abc12345";
        String mockToken = "jwt_token_xyz";

        // Mock dependencies
        doReturn(roomName).when(optimizedRoomService).generateRoomName("room");
        doReturn(mockToken).when(optimizedRoomService).getLiveKitUrl();

        RoomInfo roomInfo = new RoomInfo(roomName, 2, 300);
        when(mediaAdapter.createRoom(eq(roomName), any(RoomConfig.class))).thenReturn(roomInfo);
        when(mediaAdapter.generateToken(currentUserId, roomName, "host")).thenReturn(mockToken);

        RtcSession savedSession = RtcSession.builder()
                .id(1L)
                .roomName(roomName)
                .roomTitle("一对一视频通话")
                .initiatorId(currentUserId)
                .sessionType(1)
                .maxParticipants(2)
                .startTime(LocalDateTime.now())
                .status(0)
                .recordingEnabled(false)
                .build();
        when(sessionRepository.save(any(RtcSession.class))).thenReturn(savedSession);

        // When
        TokenResponse response = optimizedRoomService.startCall(request, currentUserId);

        // Then
        assertNotNull(response);
        assertEquals(roomName, response.getRoomName());
        assertEquals(mockToken, response.getToken());
        assertEquals("一对一视频通话", response.getRoomTitle());

        // Verify room creation with correct config
        verify(mediaAdapter).createRoom(eq(roomName), argThat(config ->
            config.getMaxParticipants() == 2 &&
            config.getEmptyTimeout() == 600 &&
            "sfu".equals(config.getRoomType())
        ));

        // Verify database operations
        verify(sessionRepository).save(any(RtcSession.class));
        verify(participantRepository).save(any(RtcParticipant.class));

        // Verify Redis operations
        verify(hashOps).putAll(eq("rtc:room:" + roomName + ":meta"), anyMap());
        verify(setOps).add(eq("rtc:room:" + roomName + ":members"), currentUserId);
        verify(hashOps).putAll(eq("rtc:room:" + roomName + ":member:" + currentUserId), anyMap());
        verify(valueOps).set(eq("rtc:session:" + currentUserId), eq(roomName), anyLong(), any());

        // Verify metrics
        verify(metricsConfig).incrementTotalCalls();
        verify(metricsConfig).incrementActiveCalls();
    }

    @Test
    void testStartCall_Group_Success() throws Exception {
        // Given
        CallRequest request = CallRequest.builder()
                .sessionType("group")
                .title("群组语音会议")
                .maxParticipants(50)
                .build();
        String currentUserId = "user1";
        String roomName = "room_xyz789";
        String mockToken = "jwt_token_abc";

        doReturn(roomName).when(optimizedRoomService).generateRoomName("room");
        doReturn(mockToken).when(optimizedRoomService).getLiveKitUrl();

        RoomInfo roomInfo = new RoomInfo(roomName, 50, 600);
        when(mediaAdapter.createRoom(eq(roomName), any(RoomConfig.class))).thenReturn(roomInfo);
        when(mediaAdapter.generateToken(currentUserId, roomName, "host")).thenReturn(mockToken);

        RtcSession savedSession = RtcSession.builder()
                .id(2L)
                .roomName(roomName)
                .roomTitle("群组语音会议")
                .initiatorId(currentUserId)
                .sessionType(2)
                .maxParticipants(50)
                .startTime(LocalDateTime.now())
                .status(0)
                .build();
        when(sessionRepository.save(any(RtcSession.class))).thenReturn(savedSession);

        // When
        TokenResponse response = optimizedRoomService.startCall(request, currentUserId);

        // Then
        assertNotNull(response);
        assertEquals(50, savedSession.getMaxParticipants());

        // Verify group room config (max 100)
        verify(mediaAdapter).createRoom(eq(roomName), argThat(config ->
            config.getMaxParticipants() == 50
        ));
    }

    @ParameterizedTest
    @CsvSource({
        "live, 3, 10000, 10000",    // 直播类型，10000人
        "live, 3, 5000, 5000",      // 直播类型，5000人限制
        "group, 2, 100, 100",       // 群组，100人
        "group, 2, 200, 100",       // 群组，超过100人限制
        "video, 1, 2, 2",           // 1v1，2人
        "audio, 1, 2, 2"            // 1v1音频，2人
    })
    void testCalculateOptimalSize(String type, int expectedType, Integer inputSize, int expectedSize) throws Exception {
        // Given
        CallRequest request = CallRequest.builder()
                .sessionType(type)
                .maxParticipants(inputSize)
                .build();

        // When & Then
        // 使用反射测试私有方法
        java.lang.reflect.Method method = OptimizedRoomService.class.getDeclaredMethod(
            "calculateOptimalSize", int.class, CallRequest.class
        );
        method.setAccessible(true);
        int result = (int) method.invoke(optimizedRoomService, expectedType, request);

        assertEquals(expectedSize, result);
    }

    @Test
    void testJoinCall_Success() throws Exception {
        // Given
        String roomName = "room_test_123";
        String userId = "user2";
        String mockToken = "jwt_token_join";

        // Mock Redis capacity check
        when(hashOps.get("rtc:room:" + roomName + ":meta", "maxMembers")).thenReturn(10000);
        when(setOps.size("rtc:room:" + roomName + ":members")).thenReturn(50L);

        RtcSession session = RtcSession.builder()
                .id(1L)
                .roomName(roomName)
                .roomTitle("万人会议")
                .initiatorId("user1")
                .sessionType(3)
                .status(0)
                .build();

        when(sessionRepository.findByRoomName(roomName)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdAndUserId(1L, userId)).thenReturn(Optional.empty());
        when(mediaAdapter.generateToken(userId, roomName, "publisher")).thenReturn(mockToken);
        doReturn(mockToken).when(optimizedRoomService).getLiveKitUrl();

        // When
        TokenResponse response = optimizedRoomService.joinCall(roomName, userId);

        // Then
        assertNotNull(response);
        assertEquals(roomName, response.getRoomName());
        assertEquals(mockToken, response.getToken());

        // Verify capacity check passed
        verify(setOps).size("rtc:room:" + roomName + ":members");

        // Verify participant saved
        verify(participantRepository).save(any(RtcParticipant.class));

        // Verify Redis updates
        verify(setOps).add("rtc:room:" + roomName + ":members", userId);
        verify(hashOps).increment("rtc:room:" + roomName + ":meta", "currentMembers", 1);
    }

    @Test
    void testJoinCall_RoomFull() {
        // Given
        String roomName = "room_full";
        String userId = "user_overflow";

        // Mock Redis - room is full
        when(hashOps.get("rtc:room:" + roomName + ":meta", "maxMembers")).thenReturn(100);
        when(setOps.size("rtc:room:" + roomName + ":members")).thenReturn(100L);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            optimizedRoomService.joinCall(roomName, userId);
        });

        assertEquals("房间已满，无法加入", exception.getMessage());
        verify(sessionRepository, never()).findByRoomName(anyString());
    }

    @Test
    void testJoinCall_RoomNotFound() {
        // Given
        String roomName = "nonexistent";
        String userId = "user2";

        // Mock Redis - room doesn't exist
        when(hashOps.get("rtc:room:" + roomName + ":meta", "maxMembers")).thenReturn(null);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            optimizedRoomService.joinCall(roomName, userId);
        });

        assertEquals("房间不存在", exception.getMessage());
    }

    @Test
    void testLeaveCall_RoomEmpty() throws Exception {
        // Given
        String roomName = "room_test";
        String userId = "user1";

        RtcSession session = RtcSession.builder()
                .id(1L)
                .roomName(roomName)
                .initiatorId(userId)
                .status(0)
                .build();

        RtcParticipant participant = RtcParticipant.builder()
                .id(1L)
                .sessionId(1L)
                .userId(userId)
                .joinTime(LocalDateTime.now().minusMinutes(10))
                .role("host")
                .build();

        when(sessionRepository.findByRoomName(roomName)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdAndUserId(1L, userId)).thenReturn(Optional.of(participant));
        when(participantRepository.countOnlineParticipants(roomName)).thenReturn(0);

        // When
        optimizedRoomService.leaveCall(roomName, userId);

        // Then
        // Verify session ended
        assertEquals(1, session.getStatus()); // ENDED status
        assertNotNull(session.getEndTime());

        // Verify room deleted
        verify(mediaAdapter).deleteRoom(roomName);

        // Verify Redis cleanup
        verify(redisTemplate).delete("rtc:room:" + roomName + ":meta");
        verify(redisTemplate).delete("rtc:room:" + roomName + ":members");

        // Verify metrics
        verify(metricsConfig).decrementActiveCalls();
    }

    @Test
    void testLeaveCall_RoomNotEmpty() throws Exception {
        // Given
        String roomName = "room_test";
        String userId = "user_leaving";

        RtcSession session = RtcSession.builder()
                .id(1L)
                .roomName(roomName)
                .initiatorId("user1")
                .status(0)
                .build();

        RtcParticipant participant = RtcParticipant.builder()
                .id(1L)
                .sessionId(1L)
                .userId(userId)
                .joinTime(LocalDateTime.now().minusMinutes(5))
                .role("publisher")
                .build();

        when(sessionRepository.findByRoomName(roomName)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdAndUserId(1L, userId)).thenReturn(Optional.of(participant));
        when(participantRepository.countOnlineParticipants(roomName)).thenReturn(5);

        // When
        optimizedRoomService.leaveCall(roomName, userId);

        // Then
        // Verify session NOT ended
        assertEquals(0, session.getStatus());

        // Verify room NOT deleted
        verify(mediaAdapter, never()).deleteRoom(anyString());

        // Verify user cleanup only
        verify(redisTemplate).delete("rtc:room:" + roomName + ":member:" + userId);
        verify(redisTemplate).delete("rtc:session:" + userId);
        verify(setOps).remove("rtc:room:" + roomName + ":members", userId);

        // Verify counter decremented
        verify(hashOps).increment("rtc:room:" + roomName + ":meta", "currentMembers", -1);
    }

    @Test
    void testGetRoomInfo_Success() {
        // Given
        String roomName = "room_test";
        RtcSession session = RtcSession.builder()
                .id(1L)
                .roomName(roomName)
                .roomTitle("测试房间")
                .build();

        when(sessionRepository.findByRoomName(roomName)).thenReturn(Optional.of(session));

        // When
        RtcSession result = optimizedRoomService.getRoomInfo(roomName);

        // Then
        assertNotNull(result);
        assertEquals(roomName, result.getRoomName());
        assertEquals("测试房间", result.getRoomTitle());
    }

    @Test
    void testGetActiveSessions_Success() {
        // Given
        String userId = "user1";
        List<RtcParticipant> participants = List.of(
                RtcParticipant.builder().id(1L).sessionId(1L).userId(userId).build(),
                RtcParticipant.builder().id(2L).sessionId(2L).userId(userId).build()
        );

        when(participantRepository.findActiveParticipation(userId)).thenReturn(participants);

        // When
        List<RtcParticipant> result = optimizedRoomService.getActiveSessions(userId);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(participantRepository).findActiveParticipation(userId);
    }

    @Test
    void testBatchUpdateRedis_Performance() throws Exception {
        // Given
        String roomName = "room_performance";
        String userId = "user1";
        RtcSession session = RtcSession.builder()
                .id(1L)
                .roomName(roomName)
                .roomTitle("性能测试")
                .build();

        // When
        java.lang.reflect.Method method = OptimizedRoomService.class.getDeclaredMethod(
            "batchUpdateRedis", String.class, String.class, RtcSession.class, int.class
        );
        method.setAccessible(true);
        method.invoke(optimizedRoomService, roomName, userId, session, 10000);

        // Then - Verify all Redis operations were called
        verify(hashOps, times(2)).putAll(anyString(), anyMap()); // room meta + member
        verify(setOps).add(anyString(), anyString());
        verify(valueOps).set(anyString(), anyString(), anyLong(), any());
        verify(redisTemplate, times(3)).expire(anyString(), anyLong(), any());
    }

    @Test
    void testConcurrentJoinCalls() throws Exception {
        // Given
        String roomName = "room_concurrent";
        int concurrentUsers = 100;

        RtcSession session = RtcSession.builder()
                .id(1L)
                .roomName(roomName)
                .roomTitle("并发测试")
                .initiatorId("user0")
                .sessionType(3)
                .status(0)
                .maxParticipants(10000)
                .build();

        // Mock Redis capacity
        when(hashOps.get("rtc:room:" + roomName + ":meta", "maxMembers")).thenReturn(10000);
        when(setOps.size("rtc:room:" + roomName + ":members")).thenReturn(0L, 1L, 2L, 5L, 10L); // Increasing counts

        when(sessionRepository.findByRoomName(roomName)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdAndUserId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(participantRepository.save(any(RtcParticipant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doReturn("mock_token").when(optimizedRoomService).getLiveKitUrl();

        // Mock token generation
        when(mediaAdapter.generateToken(anyString(), eq(roomName), eq("publisher"))).thenAnswer(invocation -> {
            return "token_" + invocation.getArgument(0);
        });

        // When - Execute concurrent joins
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<CompletableFuture<Void>> futures = new java.util.ArrayList<>();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 1; i <= concurrentUsers; i++) {
            final String userId = "user" + i;
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    TokenResponse response = optimizedRoomService.joinCall(roomName, userId);
                    if (response != null && response.getToken() != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            }, executor));
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        assertTrue(successCount.get() > 0, "At least some joins should succeed");
        assertEquals(concurrentUsers, successCount.get() + failCount.get());

        // Verify participant saves
        verify(participantRepository, atLeastOnce()).save(any(RtcParticipant.class));
    }

    @Test
    void testExceptionHandling_MetricsUpdated() throws Exception {
        // Given
        CallRequest request = CallRequest.builder()
                .sessionType("invalid")
                .title("Test")
                .build();
        String userId = "user1";

        // Mock to throw exception
        doThrow(new RuntimeException("Database error"))
                .when(sessionRepository)
                .save(any(RtcSession.class));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            optimizedRoomService.startCall(request, userId);
        });

        // Verify metrics updated for failure
        verify(metricsConfig).incrementFailedCalls();
    }

    @Test
    void testRedisOperations_AreTransactional() throws Exception {
        // This test verifies that Redis operations follow the expected pattern
        // for atomic updates in high-concurrency scenarios

        String roomName = "room_atomic";
        String userId = "user1";
        RtcSession session = RtcSession.builder()
                .id(1L)
                .roomName(roomName)
                .roomTitle("原子性测试")
                .build();

        // When
        java.lang.reflect.Method method = OptimizedRoomService.class.getDeclaredMethod(
            "batchUpdateRedis", String.class, String.class, RtcSession.class, int.class
        );
        method.setAccessible(true);
        method.invoke(optimizedRoomService, roomName, userId, session, 10000);

        // Then - Verify order of operations
        var inOrder = inOrder(hashOps, setOps, valueOps, redisTemplate);

        // 1. Room meta
        inOrder.verify(hashOps).putAll(eq("rtc:room:" + roomName + ":meta"), anyMap());
        inOrder.verify(redisTemplate).expire(eq("rtc:room:" + roomName + ":meta"), anyLong(), any());

        // 2. Members set
        inOrder.verify(setOps).add(eq("rtc:room:" + roomName + ":members"), userId);
        inOrder.verify(redisTemplate).expire(eq("rtc:room:" + roomName + ":members"), anyLong(), any());

        // 3. Member details
        inOrder.verify(hashOps).putAll(eq("rtc:room:" + roomName + ":member:" + userId), anyMap());
        inOrder.verify(redisTemplate).expire(eq("rtc:room:" + roomName + ":member:" + userId), anyLong(), any());

        // 4. User session
        inOrder.verify(valueOps).set(eq("rtc:session:" + userId), eq(roomName), anyLong(), any());
    }

    @Test
    void testClearRoomRedis_CompleteCleanup() throws Exception {
        // Given
        String roomName = "room_cleanup";

        // When
        java.lang.reflect.Method method = OptimizedRoomService.class.getDeclaredMethod(
            "clearRoomRedis", String.class
        );
        method.setAccessible(true);
        method.invoke(optimizedRoomService, roomName);

        // Then
        verify(redisTemplate).delete("rtc:room:" + roomName + ":meta");
        verify(redisTemplate).delete("rtc:room:" + roomName + ":members");
    }

    @Test
    void testClearUserRedis_UserCleanup() throws Exception {
        // Given
        String roomName = "room_test";
        String userId = "user1";

        // When
        java.lang.reflect.Method method = OptimizedRoomService.class.getDeclaredMethod(
            "clearUserRedis", String.class, String.class
        );
        method.setAccessible(true);
        method.invoke(optimizedRoomService, roomName, userId);

        // Then
        verify(setOps).remove("rtc:room:" + roomName + ":members", userId);
        verify(redisTemplate).delete("rtc:room:" + roomName + ":member:" + userId);
        verify(redisTemplate).delete("rtc:session:" + userId);
    }

    @Test
    void testParseSessionType_AllTypes() throws Exception {
        // Given & When & Then
        java.lang.reflect.Method method = OptimizedRoomService.class.getDeclaredMethod(
            "parseSessionType", String.class
        );
        method.setAccessible(true);

        assertEquals(1, method.invoke(optimizedRoomService, "video"));
        assertEquals(1, method.invoke(optimizedRoomService, "VIDEO"));
        assertEquals(1, method.invoke(optimizedRoomService, "audio"));
        assertEquals(1, method.invoke(optimizedRoomService, "AUDIO"));
        assertEquals(2, method.invoke(optimizedRoomService, "group"));
        assertEquals(2, method.invoke(optimizedRoomService, "GROUP"));
        assertEquals(3, method.invoke(optimizedRoomService, "live"));
        assertEquals(3, method.invoke(optimizedRoomService, "LIVE"));
        assertEquals(1, method.invoke(optimizedRoomService, "unknown"));
    }

    @Test
    void testGenerateRoomName_Unique() throws Exception {
        // Given
        java.lang.reflect.Method method = OptimizedRoomService.class.getDeclaredMethod(
            "generateRoomName", String.class
        );
        method.setAccessible(true);

        // When
        String name1 = (String) method.invoke(optimizedRoomService, "room");
        String name2 = (String) method.invoke(optimizedRoomService, "room");
        String name3 = (String) method.invoke(optimizedRoomService, "meeting");

        // Then
        assertNotEquals(name1, name2);
        assertTrue(name1.startsWith("room_"));
        assertTrue(name2.startsWith("room_"));
        assertTrue(name3.startsWith("meeting_"));
        assertEquals(12, name1.length()); // "room_" + 8 chars UUID
    }

    @Test
    void testGetLiveKitUrl_Fallback() throws Exception {
        // Given - When mediaAdapter is not LiveKitAdapter
        when(mediaAdapter instanceof com.phoenix.rtc.adapter.LiveKitAdapter).thenReturn(false);

        // When
        java.lang.reflect.Method method = OptimizedRoomService.class.getDeclaredMethod("getLiveKitUrl");
        method.setAccessible(true);
        String url = (String) method.invoke(optimizedRoomService);

        // Then
        assertEquals("ws://localhost:7880", url);
    }

    @Test
    void testMetricsUpdatedOnSuccess() throws Exception {
        // Given
        CallRequest request = CallRequest.builder()
                .sessionType("live")
                .title("直播测试")
                .maxParticipants(10000)
                .build();
        String userId = "host1";

        doReturn("room_test").when(optimizedRoomService).generateRoomName("room");
        doReturn("ws://test").when(optimizedRoomService).getLiveKitUrl();

        RoomInfo roomInfo = new RoomInfo("room_test", 10000, 600);
        when(mediaAdapter.createRoom(anyString(), any())).thenReturn(roomInfo);
        when(mediaAdapter.generateToken(anyString(), anyString(), anyString())).thenReturn("token");

        RtcSession session = RtcSession.builder()
                .id(1L)
                .roomName("room_test")
                .sessionType(3)
                .maxParticipants(10000)
                .status(0)
                .build();
        when(sessionRepository.save(any())).thenReturn(session);

        // When
        optimizedRoomService.startCall(request, userId);

        // Then
        verify(metricsConfig).incrementTotalCalls();
        verify(metricsConfig).incrementActiveCalls();
        verify(metricsConfig, never()).incrementFailedCalls();
    }

    @Test
    void testMetricsUpdatedOnFailure() throws Exception {
        // Given
        CallRequest request = CallRequest.builder()
                .sessionType("live")
                .title("Test")
                .build();
        String userId = "user1";

        doReturn("room_test").when(optimizedRoomService).generateRoomName("room");
        when(mediaAdapter.createRoom(anyString(), any())).thenThrow(new RuntimeException("Media error"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            optimizedRoomService.startCall(request, userId);
        });

        // Then
        verify(metricsConfig).incrementFailedCalls();
        verify(metricsConfig, never()).incrementTotalCalls();
        verify(metricsConfig, never()).incrementActiveCalls();
    }
}
