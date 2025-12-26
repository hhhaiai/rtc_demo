package com.phoenix.rtc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phoenix.rtc.model.dto.WebhookEvent;
import com.phoenix.rtc.repository.RtcSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * LiveKit Webhook 接收控制器
 * 处理 LiveKit 服务器推送的事件
 */
@RestController
@RequestMapping("/api/rtc/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final RtcSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    /**
     * 接收 LiveKit Webhook 事件
     */
    @PostMapping
    public ResponseEntity<?> handleWebhook(@RequestBody Map<String, Object> payload) {
        try {
            // 转换为 WebhookEvent 对象
            WebhookEvent event = objectMapper.convertValue(payload, WebhookEvent.class);

            log.info("收到 Webhook 事件: {}, 房间: {}", event.getEvent(),
                    event.getRoom() != null ? event.getRoom().getName() : "N/A");

            switch (event.getEvent()) {
                case "room_started":
                    handleRoomStarted(event);
                    break;
                case "room_finished":
                    handleRoomFinished(event);
                    break;
                case "participant_joined":
                    handleParticipantJoined(event);
                    break;
                case "participant_left":
                    handleParticipantLeft(event);
                    break;
                case "recording_started":
                    handleRecordingStarted(event);
                    break;
                case "recording_finished":
                    handleRecordingFinished(event);
                    break;
                default:
                    log.warn("未处理的事件类型: {}", event.getEvent());
            }

            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception e) {
            log.error("处理 Webhook 失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 房间开始
     */
    private void handleRoomStarted(WebhookEvent event) {
        String roomName = event.getRoom().getName();
        log.info("房间已开始: {}", roomName);

        // 更新数据库状态
        sessionRepository.findByRoomName(roomName).ifPresent(session -> {
            if (session.getStartTime() == null) {
                session.setStartTime(LocalDateTime.now());
                session.setStatus(0); // ACTIVE
                sessionRepository.save(session);
            }
        });
    }

    /**
     * 房间结束
     */
    private void handleRoomFinished(WebhookEvent event) {
        String roomName = event.getRoom().getName();
        log.info("房间已结束: {}", roomName);

        // 更新数据库状态
        sessionRepository.findByRoomName(roomName).ifPresent(session -> {
            if (event.getRoom().getCreationTime() != null) {
                session.setEndTime(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(event.getRoom().getCreationTime()),
                        ZoneId.systemDefault()
                ));
            } else {
                session.setEndTime(LocalDateTime.now());
            }
            session.setStatus(1); // ENDED
            sessionRepository.save(session);
        });
    }

    /**
     * 参与者加入
     */
    private void handleParticipantJoined(WebhookEvent event) {
        if (event.getParticipant() != null) {
            log.info("参与者加入 - 房间: {}, 用户: {}",
                    event.getRoom().getName(),
                    event.getParticipant().getIdentity());
        }
    }

    /**
     * 参与者离开
     */
    private void handleParticipantLeft(WebhookEvent event) {
        if (event.getParticipant() != null) {
            log.info("参与者离开 - 房间: {}, 用户: {}",
                    event.getRoom().getName(),
                    event.getParticipant().getIdentity());
        }
    }

    /**
     * 录制开始
     */
    private void handleRecordingStarted(WebhookEvent event) {
        String roomName = event.getRoom().getName();
        log.info("录制开始: {}", roomName);

        sessionRepository.findByRoomName(roomName).ifPresent(session -> {
            session.setRecordingEnabled(true);
            sessionRepository.save(session);
        });
    }

    /**
     * 录制结束
     */
    private void handleRecordingFinished(WebhookEvent event) {
        String roomName = event.getRoom().getName();
        log.info("录制结束: {}", roomName);

        // 实际场景中，这里会从 event 中获取录制文件的 URL
        // 并更新到数据库
        sessionRepository.findByRoomName(roomName).ifPresent(session -> {
            session.setRecordingUrl("https://storage.example.com/recordings/" + roomName + ".mp4");
            sessionRepository.save(session);
        });
    }
}
