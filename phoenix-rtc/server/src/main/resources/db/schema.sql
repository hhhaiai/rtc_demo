-- Phoenix RTC 数据库初始化脚本

-- 创建数据库 (如果不存在)
-- CREATE DATABASE IF NOT EXISTS phoenix_rtc CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE phoenix_rtc;

-- 通话会话表
CREATE TABLE IF NOT EXISTS rtc_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_name VARCHAR(64) NOT NULL UNIQUE COMMENT 'LiveKit 房间名',
    room_title VARCHAR(128) COMMENT '房间标题',
    initiator_id VARCHAR(32) NOT NULL COMMENT '发起人 ID',
    session_type TINYINT NOT NULL COMMENT '1:1v1, 2:群聊, 3:直播',
    max_participants INT DEFAULT 100,
    start_time DATETIME,
    end_time DATETIME,
    status TINYINT DEFAULT 0 COMMENT '0:进行中, 1:已结束, 2:异常',
    recording_enabled BOOLEAN DEFAULT FALSE,
    recording_url VARCHAR(512),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_initiator (initiator_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RTC 通话会话表';

-- 通话成员表
CREATE TABLE IF NOT EXISTS rtc_participant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    user_name VARCHAR(64),
    join_time DATETIME,
    leave_time DATETIME,
    role VARCHAR(16) DEFAULT 'publisher' COMMENT 'publisher/subscriber/host',
    duration INT DEFAULT 0 COMMENT '通话时长(秒)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id),
    INDEX idx_user (user_id),
    INDEX idx_leave_time (leave_time),
    FOREIGN KEY (session_id) REFERENCES rtc_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RTC 通话成员表';

-- 录制记录表
CREATE TABLE IF NOT EXISTS rtc_recording (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    file_url VARCHAR(512) NOT NULL,
    file_size BIGINT,
    duration INT,
    format VARCHAR(16) DEFAULT 'mp4',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id),
    FOREIGN KEY (session_id) REFERENCES rtc_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='录制记录表';

-- 插入测试数据
INSERT IGNORE INTO rtc_session (id, room_name, room_title, initiator_id, session_type, max_participants, status)
VALUES (1, 'test_room_001', '测试房间', 'user_demo_001', 1, 10, 1);

INSERT IGNORE INTO rtc_participant (session_id, user_id, user_name, role, join_time, leave_time, duration)
VALUES (1, 'user_demo_001', '测试用户1', 'host', NOW(), DATE_SUB(NOW(), INTERVAL 300 SECOND), 300);
