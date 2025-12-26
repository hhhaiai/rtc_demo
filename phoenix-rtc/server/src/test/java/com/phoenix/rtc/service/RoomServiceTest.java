package com.phoenix.rtc.service;

import com.phoenix.rtc.model.dto.CallRequest;
import com.phoenix.rtc.model.dto.TokenResponse;
import com.phoenix.rtc.model.entity.RtcSession;
import com.phoenix.rtc.repository.RtcParticipantRepository;
import com.phoenix.rtc.repository.RtcSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RoomService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private LiveKitService liveKitService;

    @Mock
    private RtcSessionRepository sessionRepository;

    @Mock
    private RtcParticipantRepository participantRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private RoomService roomService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(mock(org.springframework.data.redis.core.HashOperations.class));
    }

    @Test
    void testStartCall_Success() {
        // Given
        CallRequest request = CallRequest.builder()
                .targetUserIds(List.of("user2", "user3"))
                .sessionType("video")
                .title("测试通话")
                .isGroup(false)
                .build();

        String currentUserId = "user1";
        String roomName = "room_test_123";
        String mockToken = "mock_jwt_token";

        when(liveKitService.generateRoomName(anyString())).thenReturn(roomName);
        when(liveKitService.createRoom(anyString(), anyInt(), anyInt())).thenReturn(null);
        when(liveKitService.generateToken(anyString(), anyString(), anyString(), any())).thenReturn(mockToken);
        when(liveKitService.getLiveKitUrl()).thenReturn("ws://localhost:7880");

        RtcSession savedSession = RtcSession.builder()
                .id(1L)
                .roomName(roomName)
                .initiatorId(currentUserId)
                .sessionType(1)
                .status(0)
                .build();

        when(sessionRepository.save(any(RtcSession.class))).thenReturn(savedSession);

        // When
        TokenResponse response = roomService.startCall(request, currentUserId);

        // Then
        assertNotNull(response);
        assertEquals(roomName, response.getRoomName());
        assertEquals(mockToken, response.getToken());
        assertEquals("ws://localhost:7880", response.getUrl());

        verify(liveKitService, times(1)).generateRoomName("room");
        verify(liveKitService, times(1)).createRoom(roomName, 300, 10);
        verify(sessionRepository, times(1)).save(any(RtcSession.class));
        verify(participantRepository, times(1)).save(any());
    }

    @Test
    void testJoinCall_Success() {
        // Given
        String roomName = "room_test_123";
        String currentUserId = "user2";
        String mockToken = "mock_jwt_token";

        RtcSession session = RtcSession.builder()
                .id(1L)
                .roomName(roomName)
                .initiatorId("user1")
                .sessionType(1)
                .status(0)
                .build();

        when(sessionRepository.findByRoomName(roomName)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdAndUserId(1L, currentUserId)).thenReturn(Optional.empty());
        when(liveKitService.generateToken(currentUserId, roomName, "publisher", null)).thenReturn(mockToken);
        when(liveKitService.getLiveKitUrl()).thenReturn("ws://localhost:7880");

        // When
        TokenResponse response = roomService.joinCall(roomName, currentUserId);

        // Then
        assertNotNull(response);
        assertEquals(roomName, response.getRoomName());
        assertEquals(mockToken, response.getToken());

        verify(participantRepository, times(1)).save(any());
        verify(sessionRepository, times(1)).findByRoomName(roomName);
    }

    @Test
    void testJoinCall_RoomNotFound() {
        // Given
        String roomName = "nonexistent";

        when(sessionRepository.findByRoomName(roomName)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            roomService.joinCall(roomName, "user2");
        });
    }

    @Test
    void testLeaveCall_Success() {
        // Given
        String roomName = "room_test_123";
        String userId = "user1";

        RtcSession session = RtcSession.builder()
                .id(1L)
                .roomName(roomName)
                .initiatorId(userId)
                .status(0)
                .build();

        when(sessionRepository.findByRoomName(roomName)).thenReturn(Optional.of(session));

        // When
        roomService.leaveCall(roomName, userId);

        // Then
        verify(sessionRepository, times(1)).findByRoomName(roomName);
        verify(participantRepository, times(1)).findBySessionIdAndUserId(anyLong(), eq(userId));
    }

    @Test
    void testGetRoomInfo_Success() {
        // Given
        String roomName = "room_test_123";
        RtcSession session = RtcSession.builder()
                .id(1L)
                .roomName(roomName)
                .build();

        when(sessionRepository.findByRoomName(roomName)).thenReturn(Optional.of(session));

        // When
        RtcSession result = roomService.getRoomInfo(roomName);

        // Then
        assertNotNull(result);
        assertEquals(roomName, result.getRoomName());
    }
}
