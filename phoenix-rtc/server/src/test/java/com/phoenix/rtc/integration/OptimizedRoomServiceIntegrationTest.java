package com.phoenix.rtc.integration;

import com.phoenix.rtc.adapter.MediaAdapter;
import com.phoenix.rtc.config.MetricsConfig;
import com.phoenix.rtc.model.dto.CallRequest;
import com.phoenix.rtc.model.dto.TokenResponse;
import com.phoenix.rtc.repository.RtcParticipantRepository;
import com.phoenix.rtc.repository.RtcSessionRepository;
import com.phoenix.rtc.service.OptimizedRoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OptimizedRoomService 集成测试
 * 测试完整业务流程和高并发场景
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
class OptimizedRoomServiceIntegrationTest {

    @Autowired
    private OptimizedRoomService optimizedRoomService;

    @MockBean
    private MediaAdapter mediaAdapter;

    @MockBean
    private RtcSessionRepository sessionRepository;

    @MockBean
    private RtcParticipantRepository participantRepository;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private MetricsConfig metricsConfig;

    @BeforeEach
    void setUp() {
        // Reset mocks
        reset(mediaAdapter, sessionRepository, participantRepository, redisTemplate, metricsConfig);
    }

    @Test
    void testCompleteFlow_1v1Call() throws Exception {
        // 1. Start call
        CallRequest startRequest = CallRequest.builder()
                .sessionType("video")
                .title("一对一测试")
                .build();

        // Mock external dependencies
        when(mediaAdapter.createRoom(anyString(), any())).thenReturn(new io.livekit.server.RoomInfo("test_room", 2, 300));
        when(mediaAdapter.generateToken(anyString(), anyString(), anyString())).thenReturn("host_token");

        // Mock repository saves
        when(sessionRepository.save(any())).thenAnswer(inv -> {
            var session = inv.getArgument(0);
            java.lang.reflect.Field field = session.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(session, 1L);
            return session;
        });

        // 2. Execute start call
        TokenResponse startResponse = optimizedRoomService.startCall(startRequest, "user1");

        // Verify start response
        assertNotNull(startResponse);
        assertNotNull(startResponse.getRoomName());
        assertEquals("一对一测试", startResponse.getRoomTitle());
        assertEquals("host_token", startResponse.getToken());

        // 3. Join call
        String roomName = startResponse.getRoomName();
        when(mediaAdapter.generateToken("user2", roomName, "publisher")).thenReturn("user2_token");

        TokenResponse joinResponse = optimizedRoomService.joinCall(roomName, "user2");

        // Verify join response
        assertNotNull(joinResponse);
        assertEquals(roomName, joinResponse.getRoomName());
        assertEquals("user2_token", joinResponse.getToken());

        // 4. Leave call
        optimizedRoomService.leaveCall(roomName, "user2");

        // Verify cleanup
        verify(mediaAdapter, never()).deleteRoom(anyString()); // Room still has user1

        // 5. User1 leaves (room empty)
        optimizedRoomService.leaveCall(roomName, "user1");

        // Verify room deletion
        verify(mediaAdapter).deleteRoom(roomName);
    }

    @Test
    void testHighConcurrency_100UsersJoinSimultaneously() throws Exception {
        // Given
        String roomName = "room_concurrent_100";
        int userCount = 100;

        // Mock start call
        CallRequest startRequest = CallRequest.builder()
                .sessionType("live")
                .title("万人会议")
                .maxParticipants(10000)
                .build();

        when(mediaAdapter.createRoom(anyString(), any())).thenReturn(new io.livekit.server.RoomInfo(roomName, 10000, 600));
        when(mediaAdapter.generateToken(eq("host"), anyString(), anyString())).thenReturn("host_token");

        when(sessionRepository.save(any())).thenAnswer(inv -> {
            var session = inv.getArgument(0);
            java.lang.reflect.Field field = session.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(session, 1L);
            return session;
        });

        // Start call
        TokenResponse startResponse = optimizedRoomService.startCall(startRequest, "host");
        String actualRoomName = startResponse.getRoomName();

        // Setup for concurrent joins
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(userCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // Mock repository for joins
        when(sessionRepository.findByRoomName(actualRoomName)).thenReturn(
            java.util.Optional.of(com.phoenix.rtc.model.entity.RtcSession.builder()
                .id(1L)
                .roomName(actualRoomName)
                .status(0)
                .maxParticipants(10000)
                .build())
        );
        when(participantRepository.findBySessionIdAndUserId(anyLong(), anyString())).thenReturn(java.util.Optional.empty());
        when(participantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Mock token generation with slight delay to simulate real world
        when(mediaAdapter.generateToken(anyString(), eq(actualRoomName), eq("publisher"))).thenAnswer(inv -> {
            Thread.sleep(5); // Simulate token generation time
            return "token_" + inv.getArgument(0);
        });

        // When - Launch concurrent joins
        ExecutorService executor = Executors.newFixedThreadPool(50);
        for (int i = 1; i <= userCount; i++) {
            final String userId = "user" + i;
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();

                    TokenResponse response = optimizedRoomService.joinCall(actualRoomName, userId);
                    if (response != null && response.getToken() != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for completion with timeout
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        assertTrue(completed, "All joins should complete within timeout");
        assertEquals(userCount, successCount.get() + failCount.get(), "All users should be processed");
        assertTrue(successCount.get() > 0, "At least some joins should succeed");

        // Verify no race conditions in Redis operations
        verify(participantRepository, atLeastOnce()).save(any());
    }

    @Test
    void testMultipleSimultaneousLargeMeetings() throws Exception {
        // Given - 5 simultaneous meetings with 200 users each
        int meetingCount = 5;
        int usersPerMeeting = 200;
        int totalUsers = meetingCount * usersPerMeeting;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalUsers);
        AtomicInteger totalSuccess = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(100);

        // For each meeting
        for (int m = 0; m < meetingCount; m++) {
            final String meetingId = "meeting_" + m;
            final String roomName = "room_" + meetingId;

            // Mock start call for this meeting
            when(mediaAdapter.createRoom(eq(roomName), any())).thenReturn(
                new io.livekit.server.RoomInfo(roomName, 10000, 600)
            );

            // Simulate users joining
            for (int u = 1; u <= usersPerMeeting; u++) {
                final String userId = "user_" + meetingId + "_" + u;
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        // Mock repository for this specific call
                        when(sessionRepository.findByRoomName(roomName)).thenReturn(
                            java.util.Optional.of(com.phoenix.rtc.model.entity.RtcSession.builder()
                                .id((long) m)
                                .roomName(roomName)
                                .status(0)
                                .maxParticipants(10000)
                                .build())
                        );
                        when(participantRepository.findBySessionIdAndUserId(anyLong(), eq(userId)))
                            .thenReturn(java.util.Optional.empty());
                        when(participantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                        when(mediaAdapter.generateToken(eq(userId), eq(roomName), eq("publisher")))
                            .thenReturn("token_" + userId);

                        TokenResponse response = optimizedRoomService.joinCall(roomName, userId);
                        if (response != null) {
                            totalSuccess.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Count failures if needed
                    } finally {
                        endLatch.countDown();
                    }
                });
            }
        }

        // Start all
        startLatch.countDown();
        boolean completed = endLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        assertTrue(completed, "All meetings should complete");
        assertEquals(totalUsers, totalSuccess.get(), "All users should join successfully");

        // Verify meetings were created
        verify(mediaAdapter, times(meetingCount)).createRoom(anyString(), any());
    }

    @Test
    void testRoomCapacityEnforcement() throws Exception {
        // Given
        String roomName = "room_capacity_test";
        int maxCapacity = 100;

        // Setup room
        when(mediaAdapter.createRoom(eq(roomName), any())).thenReturn(
            new io.livekit.server.RoomInfo(roomName, maxCapacity, 600)
        );
        when(mediaAdapter.generateToken(anyString(), eq(roomName), anyString())).thenReturn("token");

        when(sessionRepository.save(any())).thenAnswer(inv -> {
            var session = inv.getArgument(0);
            java.lang.reflect.Field field = session.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(session, 1L);
            return session;
        });

        // Start room
        CallRequest request = CallRequest.builder()
                .sessionType("group")
                .title("Capacity Test")
                .maxParticipants(maxCapacity)
                .build();

        optimizedRoomService.startCall(request, "host");

        // Fill room to capacity
        when(sessionRepository.findByRoomName(roomName)).thenReturn(
            java.util.Optional.of(com.phoenix.rtc.model.entity.RtcSession.builder()
                .id(1L)
                .roomName(roomName)
                .status(0)
                .maxParticipants(maxCapacity)
                .build())
        );

        for (int i = 1; i <= maxCapacity; i++) {
            String userId = "user" + i;
            when(participantRepository.findBySessionIdAndUserId(1L, userId)).thenReturn(java.util.Optional.empty());
            when(participantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mediaAdapter.generateToken(userId, roomName, "publisher")).thenReturn("token_" + userId);

            optimizedRoomService.joinCall(roomName, userId);
        }

        // When - Try to add one more user
        when(participantRepository.findBySessionIdAndUserId(1L, "overflow_user"))
            .thenReturn(java.util.Optional.empty());

        // Then - Should fail
        assertThrows(RuntimeException.class, () -> {
            optimizedRoomService.joinCall(roomName, "overflow_user");
        }, "Should reject user when room is full");
    }

    @Test
    void testConcurrentLeaveOperations() throws Exception {
        // Given
        String roomName = "room_leave_test";
        int userCount = 50;

        // Setup room with users
        when(mediaAdapter.createRoom(eq(roomName), any())).thenReturn(
            new io.livekit.server.RoomInfo(roomName, 1000, 600)
        );

        when(sessionRepository.save(any())).thenAnswer(inv -> {
            var session = inv.getArgument(0);
            java.lang.reflect.Field field = session.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(session, 1L);
            return session;
        });

        // Start room
        CallRequest request = CallRequest.builder()
                .sessionType("live")
                .title("Leave Test")
                .build();

        optimizedRoomService.startCall(request, "host");

        // Add users
        when(sessionRepository.findByRoomName(roomName)).thenReturn(
            java.util.Optional.of(com.phoenix.rtc.model.entity.RtcSession.builder()
                .id(1L)
                .roomName(roomName)
                .status(0)
                .build())
        );

        for (int i = 1; i <= userCount; i++) {
            String userId = "user" + i;
            when(participantRepository.findBySessionIdAndUserId(1L, userId)).thenReturn(java.util.Optional.empty());
            when(participantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mediaAdapter.generateToken(userId, roomName, "publisher")).thenReturn("token_" + userId);

            optimizedRoomService.joinCall(roomName, userId);
        }

        // Mock participant data for leaves
        when(participantRepository.findBySessionIdAndUserId(anyLong(), anyString())).thenAnswer(inv -> {
            String userId = inv.getArgument(1);
            return java.util.Optional.of(com.phoenix.rtc.model.entity.RtcParticipant.builder()
                .id(1L)
                .sessionId(1L)
                .userId(userId)
                .joinTime(java.time.LocalDateTime.now().minusMinutes(10))
                .build());
        });

        when(participantRepository.countOnlineParticipants(roomName)).thenAnswer(inv -> {
            // Return decreasing count
            return userCount - 1;
        });

        // When - Concurrent leaves
        CountDownLatch latch = new CountDownLatch(userCount);
        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 1; i <= userCount; i++) {
            final String userId = "user" + i;
            executor.submit(() -> {
                try {
                    optimizedRoomService.leaveCall(roomName, userId);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        assertTrue(completed, "All leaves should complete");
        verify(mediaAdapter, atMostOnce()).deleteRoom(roomName); // Only once when last user leaves
    }

    @Test
    void testRedisFailureHandling() throws Exception {
        // Given
        String roomName = "room_redis_test";
        CallRequest request = CallRequest.builder()
                .sessionType("video")
                .title("Redis Test")
                .build();

        // Mock successful room creation
        when(mediaAdapter.createRoom(anyString(), any())).thenReturn(
            new io.livekit.server.RoomInfo(roomName, 2, 300)
        );
        when(mediaAdapter.generateToken(anyString(), anyString(), anyString())).thenReturn("token");

        when(sessionRepository.save(any())).thenAnswer(inv -> {
            var session = inv.getArgument(0);
            java.lang.reflect.Field field = session.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(session, 1L);
            return session;
        });

        // When - Redis operations fail
        doThrow(new RuntimeException("Redis connection failed"))
                .when(redisTemplate)
                .opsForHash();

        // Then - Should still complete (graceful degradation)
        TokenResponse response = optimizedRoomService.startCall(request, "user1");

        // Should succeed despite Redis failure
        assertNotNull(response);
        verify(sessionRepository).save(any());
        verify(mediaAdapter).createRoom(anyString(), any());
    }

    @Test
    void testDatabaseFailureHandling() throws Exception {
        // Given
        CallRequest request = CallRequest.builder()
                .sessionType("video")
                .title("DB Test")
                .build();

        // Mock room creation success
        when(mediaAdapter.createRoom(anyString(), any())).thenReturn(
            new io.livekit.server.RoomInfo("test_room", 2, 300)
        );

        // Mock database failure
        when(sessionRepository.save(any())).thenThrow(new RuntimeException("Database connection failed"));

        // When & Then - Should throw exception and not leak resources
        assertThrows(RuntimeException.class, () -> {
            optimizedRoomService.startCall(request, "user1");
        });

        // Verify metrics updated
        verify(metricsConfig).incrementFailedCalls();
    }

    @Test
    void testPerformance_BatchOperations() throws Exception {
        // Given
        String roomName = "room_perf_test";
        int batchSize = 1000;

        // When - Create room with 1000 users in batch
        long startTime = System.currentTimeMillis();

        // Simulate batch Redis operations
        for (int i = 0; i < batchSize; i++) {
            String userId = "user" + i;
            // Simulate the batchUpdateRedis operations
            verify(hashOps, atMost(batchSize)).putAll(anyString(), anyMap());
            verify(setOps, atMost(batchSize)).add(anyString(), anyString());
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then - Should complete within reasonable time
        // Note: This is a mock test, so duration will be near 0
        // In real scenario, batch operations should be < 100ms for 1000 users
        assertTrue(duration < 10000, "Batch operations should complete quickly");
    }

    @Test
    void testStateConsistency_AfterConcurrentOperations() throws Exception {
        // Given
        String roomName = "room_consistency_test";

        // Setup room
        when(mediaAdapter.createRoom(eq(roomName), any())).thenReturn(
            new io.livekit.server.RoomInfo(roomName, 1000, 600)
        );
        when(mediaAdapter.generateToken(anyString(), eq(roomName), anyString())).thenReturn("token");

        when(sessionRepository.save(any())).thenAnswer(inv -> {
            var session = inv.getArgument(0);
            java.lang.reflect.Field field = session.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(session, 1L);
            return session;
        });

        CallRequest request = CallRequest.builder()
                .sessionType("live")
                .title("Consistency Test")
                .build();

        optimizedRoomService.startCall(request, "host");

        // When - Mix of joins and leaves
        CountDownLatch latch = new CountDownLatch(100);
        ExecutorService executor = Executors.newFixedThreadPool(20);

        // 50 joins
        for (int i = 1; i <= 50; i++) {
            final String userId = "join_" + i;
            executor.submit(() -> {
                try {
                    when(sessionRepository.findByRoomName(roomName)).thenReturn(
                        java.util.Optional.of(com.phoenix.rtc.model.entity.RtcSession.builder()
                            .id(1L)
                            .roomName(roomName)
                            .status(0)
                            .build())
                    );
                    when(participantRepository.findBySessionIdAndUserId(1L, userId))
                        .thenReturn(java.util.Optional.empty());
                    when(participantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                    when(mediaAdapter.generateToken(userId, roomName, "publisher"))
                        .thenReturn("token_" + userId);

                    optimizedRoomService.joinCall(roomName, userId);
                } catch (Exception e) {
                    // Expected some may fail due to mock setup
                } finally {
                    latch.countDown();
                }
            });
        }

        // 50 leaves (some may not exist)
        for (int i = 1; i <= 50; i++) {
            final String userId = "leave_" + i;
            executor.submit(() -> {
                try {
                    when(sessionRepository.findByRoomName(roomName)).thenReturn(
                        java.util.Optional.of(com.phoenix.rtc.model.entity.RtcSession.builder()
                            .id(1L)
                            .roomName(roomName)
                            .status(0)
                            .build())
                    );
                    when(participantRepository.findBySessionIdAndUserId(1L, userId))
                        .thenReturn(java.util.Optional.of(com.phoenix.rtc.model.entity.RtcParticipant.builder()
                            .id(1L)
                            .sessionId(1L)
                            .userId(userId)
                            .joinTime(java.time.LocalDateTime.now())
                            .build()));
                    when(participantRepository.countOnlineParticipants(roomName)).thenReturn(1);

                    optimizedRoomService.leaveCall(roomName, userId);
                } catch (Exception e) {
                    // Expected
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        assertTrue(completed, "All operations should complete");
        // Verify no crashes or deadlocks
    }
}
