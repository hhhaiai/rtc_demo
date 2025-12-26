package com.phoenix.rtc.service;

import io.livekit.server.LiveKitServerClient;
import io.livekit.server.RoomInfo;
import io.livekit.server.TokenOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * LiveKitService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class LiveKitServiceTest {

    @Mock
    private LiveKitServerClient liveKitClient;

    @InjectMocks
    private LiveKitService liveKitService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(liveKitService, "liveKitUrl", "ws://localhost:7880");
        ReflectionTestUtils.setField(liveKitService, "apiKey", "test_key");
        ReflectionTestUtils.setField(liveKitService, "apiSecret", "test_secret");
    }

    @Test
    void testGenerateToken_Success() throws Exception {
        // Given
        String userId = "user123";
        String roomName = "room123";
        String role = "host";
        String expectedToken = "mock_jwt_token";

        when(liveKitClient.createToken(anyString(), anyString(), any(TokenOptions.class)))
                .thenReturn(expectedToken);

        // When
        String token = liveKitService.generateToken(userId, roomName, role, null);

        // Then
        assertNotNull(token);
        assertEquals(expectedToken, token);
        verify(liveKitClient, times(1)).createToken(anyString(), anyString(), any(TokenOptions.class));
    }

    @Test
    void testGenerateToken_ThrowsException() {
        // Given
        when(liveKitClient.createToken(anyString(), anyString(), any(TokenOptions.class)))
                .thenThrow(new RuntimeException("Token generation failed"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            liveKitService.generateToken("user123", "room123", "host", null);
        });
    }

    @Test
    void testCreateRoom_Success() throws Exception {
        // Given
        String roomName = "test_room";
        Integer emptyTimeout = 300;
        Integer maxParticipants = 10;

        RoomInfo mockRoomInfo = new RoomInfo();
        mockRoomInfo.setName(roomName);

        when(liveKitClient.createRoom(any())).thenReturn(mockRoomInfo);

        // When
        RoomInfo result = liveKitService.createRoom(roomName, emptyTimeout, maxParticipants);

        // Then
        assertNotNull(result);
        assertEquals(roomName, result.getName());
        verify(liveKitClient, times(1)).createRoom(any());
    }

    @Test
    void testGetRoomInfo_Success() throws Exception {
        // Given
        String roomName = "test_room";
        RoomInfo mockRoomInfo = new RoomInfo();
        mockRoomInfo.setName(roomName);

        when(liveKitClient.getRoom(roomName)).thenReturn(mockRoomInfo);

        // When
        RoomInfo result = liveKitService.getRoomInfo(roomName);

        // Then
        assertNotNull(result);
        assertEquals(roomName, result.getName());
    }

    @Test
    void testGetRoomInfo_NotFound() throws Exception {
        // Given
        String roomName = "nonexistent";
        when(liveKitClient.getRoom(roomName)).thenThrow(new RuntimeException("Room not found"));

        // When
        RoomInfo result = liveKitService.getRoomInfo(roomName);

        // Then
        assertNull(result);
    }

    @Test
    void testGenerateRoomName() {
        // Given
        String prefix = "room";

        // When
        String roomName1 = liveKitService.generateRoomName(prefix);
        String roomName2 = liveKitService.generateRoomName(prefix);

        // Then
        assertNotNull(roomName1);
        assertNotNull(roomName2);
        assertTrue(roomName1.startsWith(prefix + "_"));
        assertTrue(roomName2.startsWith(prefix + "_"));
        assertNotEquals(roomName1, roomName2); // 应该是唯一的
    }

    @Test
    void testGetLiveKitUrl() {
        // When
        String url = liveKitService.getLiveKitUrl();

        // Then
        assertEquals("ws://localhost:7880", url);
    }
}
