package com.phoenix.rtc.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LiveKit Webhook 事件 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {

    /**
     * 事件类型
     */
    private String event;

    /**
     * 房间信息
     */
    private RoomInfo room;

    /**
     * 参与者信息 (部分事件有)
     */
    private ParticipantInfo participant;

    /**
     * 事件时间戳
     */
    @JsonProperty("created_at")
    private Long createdAt;

    /**
     * 房间信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoomInfo {
        private String name;
        private String sid;
        private String emptyTimeout;
        private String maxParticipants;
        private Long creationTime;
    }

    /**
     * 参与者信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantInfo {
        private String identity;
        private String name;
        private String sid;
    }
}
