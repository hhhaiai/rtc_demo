package com.phoenix.rtc.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * RTC 通话成员实体
 */
@Entity
@Table(name = "rtc_participant")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RtcParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false, length = 32)
    private String userId;

    @Column(length = 64)
    private String userName;

    @Column
    private LocalDateTime joinTime;

    @Column
    private LocalDateTime leaveTime;

    @Column(length = 16)
    private String role; // publisher/subscriber/host

    @Column
    private Integer duration; // 通话时长(秒)

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // 角色枚举
    public enum Role {
        PUBLISHER("publisher", "发布者(可推流)"),
        SUBSCRIBER("subscriber", "订阅者(只看)"),
        HOST("host", "主持人");

        private final String code;
        private final String desc;

        Role(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public String getCode() {
            return code;
        }
    }
}
