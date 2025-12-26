package com.phoenix.rtc.adapter;

import io.livekit.server.RoomInfo;

/**
 * 媒体服务器适配器接口
 * 为未来支持多种媒体服务器(Kurento/LiveKit/SRS)预留扩展性
 *
 * 参考 n.md Module 5: Media Capability API
 */
public interface MediaAdapter {

    /**
     * 创建房间
     *
     * @param name 房间名称
     * @param config 房间配置
     * @return 房间信息
     */
    RoomInfo createRoom(String name, RoomConfig config);

    /**
     * 生成加入房间的 Token
     *
     * @param userId 用户ID
     * @param roomName 房间名称
     * @param role 角色 (host/publisher/subscriber)
     * @return JWT Token
     */
    String generateToken(String userId, String roomName, String role);

    /**
     * 删除房间
     *
     * @param roomName 房间名称
     */
    void deleteRoom(String roomName);

    /**
     * 获取房间信息
     *
     * @param roomName 房间名称
     * @return 房间信息
     */
    RoomInfo getRoomInfo(String roomName);

    /**
     * 房间配置类
     */
    class RoomConfig {
        private Integer emptyTimeout;      // 空房间超时时间(秒)
        private Integer maxParticipants;   // 最大参与人数
        private String roomType;           // 房间类型: p2p/sfu/broadcast
        private Boolean recordingEnabled;  // 是否启用录制

        public static RoomConfigBuilder builder() {
            return new RoomConfigBuilder();
        }

        public Integer getEmptyTimeout() {
            return emptyTimeout;
        }

        public void setEmptyTimeout(Integer emptyTimeout) {
            this.emptyTimeout = emptyTimeout;
        }

        public Integer getMaxParticipants() {
            return maxParticipants;
        }

        public void setMaxParticipants(Integer maxParticipants) {
            this.maxParticipants = maxParticipants;
        }

        public String getRoomType() {
            return roomType;
        }

        public void setRoomType(String roomType) {
            this.roomType = roomType;
        }

        public Boolean getRecordingEnabled() {
            return recordingEnabled;
        }

        public void setRecordingEnabled(Boolean recordingEnabled) {
            this.recordingEnabled = recordingEnabled;
        }

        public static class RoomConfigBuilder {
            private Integer emptyTimeout;
            private Integer maxParticipants;
            private String roomType;
            private Boolean recordingEnabled;

            public RoomConfigBuilder emptyTimeout(Integer emptyTimeout) {
                this.emptyTimeout = emptyTimeout;
                return this;
            }

            public RoomConfigBuilder maxParticipants(Integer maxParticipants) {
                this.maxParticipants = maxParticipants;
                return this;
            }

            public RoomConfigBuilder roomType(String roomType) {
                this.roomType = roomType;
                return this;
            }

            public RoomConfigBuilder recordingEnabled(Boolean recordingEnabled) {
                this.recordingEnabled = recordingEnabled;
                return this;
            }

            public RoomConfig build() {
                RoomConfig config = new RoomConfig();
                config.setEmptyTimeout(this.emptyTimeout);
                config.setMaxParticipants(this.maxParticipants);
                config.setRoomType(this.roomType);
                config.setRecordingEnabled(this.recordingEnabled);
                return config;
            }
        }
    }
}
