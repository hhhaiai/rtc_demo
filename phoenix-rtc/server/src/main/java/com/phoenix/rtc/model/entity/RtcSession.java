package com.phoenix.rtc.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * RTC 通话会话实体
 */
@Entity
@Table(name = "rtc_session")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RtcSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String roomName;

    @Column(length = 128)
    private String roomTitle;

    @Column(nullable = false, length = 32)
    private String initiatorId;

    @Column(nullable = false)
    private Integer sessionType; // 1:1v1, 2:群聊, 3:直播

    @Column
    private Integer maxParticipants;

    @Column
    private LocalDateTime startTime;

    @Column
    private LocalDateTime endTime;

    @Column(nullable = false)
    private Integer status; // 0:进行中, 1:已结束, 2:异常

    @Column
    private Boolean recordingEnabled;

    @Column(length = 512)
    private String recordingUrl;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // 会话类型枚举
    public enum SessionType {
        ONE_V_ONE(1, "1v1通话"),
        GROUP(2, "群聊"),
        LIVE(3, "直播");

        private final int code;
        private final String desc;

        SessionType(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public int getCode() {
            return code;
        }

        public static SessionType fromCode(int code) {
            for (SessionType type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            return ONE_V_ONE;
        }
    }

    // 状态枚举
    public enum Status {
        ACTIVE(0),
        ENDED(1),
        ERROR(2);

        private final int code;

        Status(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }
}
