import React, { useState } from 'react';
import { useCallSession } from '../hooks/useCallSession';

/**
 * 主页 - 桌面端
 */
export const HomeScreen = ({ onStartCall }) => {
  const [targetUserIds, setTargetUserIds] = useState('');
  const [roomName, setRoomName] = useState('');
  const [sessionType, setSessionType] = useState('video');

  const { startCall, joinCall } = useCallSession();

  const handleStartCall = async () => {
    const ids = targetUserIds.split(',').map(id => id.trim()).filter(id => id);
    if (ids.length === 0) {
      alert('请输入目标用户ID');
      return;
    }

    try {
      await startCall({
        targetUserIds: ids,
        sessionType: sessionType,
        title: '桌面通话',
      });
      onStartCall();
    } catch (e) {
      alert('发起通话失败: ' + e.message);
    }
  };

  const handleJoinRoom = async () => {
    if (!roomName.trim()) {
      alert('请输入房间名称');
      return;
    }

    try {
      await joinCall(roomName.trim());
      onStartCall();
    } catch (e) {
      alert('加入房间失败: ' + e.message);
    }
  };

  return (
    <div style={styles.container}>
      <div style={styles.content}>
        <h1 style={styles.title}>Phoenix RTC 桌面客户端</h1>
        <p style={styles.subtitle}>支持 Windows / macOS / Linux</p>

        <div style={styles.section}>
          <h2>发起通话</h2>
          <div style={styles.inputGroup}>
            <label>目标用户ID (多个用逗号分隔)</label>
            <input
              type="text"
              placeholder="例如: user2,user3"
              value={targetUserIds}
              onChange={(e) => setTargetUserIds(e.target.value)}
              style={styles.input}
            />
          </div>

          <div style={styles.inputGroup}>
            <label>通话类型</label>
            <select
              value={sessionType}
              onChange={(e) => setSessionType(e.target.value)}
              style={styles.input}
            >
              <option value="video">视频通话</option>
              <option value="audio">语音通话</option>
              <option value="live">直播</option>
            </select>
          </div>

          <button onClick={handleStartCall} style={styles.primaryButton}>
            发起通话
          </button>
        </div>

        <div style={styles.section}>
          <h2>加入房间</h2>
          <div style={styles.inputGroup}>
            <label>房间名称</label>
            <input
              type="text"
              placeholder="输入房间名称"
              value={roomName}
              onChange={(e) => setRoomName(e.target.value)}
              style={styles.input}
            />
          </div>

          <button onClick={handleJoinRoom} style={styles.secondaryButton}>
            加入房间
          </button>
        </div>

        <div style={styles.info}>
          <p>💡 提示：</p>
          <p>1. 确保后端服务已启动 (Docker + Spring Boot)</p>
          <p>2. 输入用户ID发起通话或加入现有房间</p>
          <p>3. 支持多人视频会议和直播模式</p>
        </div>
      </div>
    </div>
  );
};

const styles = {
  container: {
    width: '100%',
    height: '100%',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    background: 'linear-gradient(135deg, #0a0a0a 0%, #1a1a2e 100%)',
  },
  content: {
    width: 500,
    padding: 40,
    background: 'rgba(255, 255, 255, 0.05)',
    borderRadius: 16,
    backdropFilter: 'blur(10px)',
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    marginBottom: 8,
    color: '#fff',
  },
  subtitle: {
    fontSize: 14,
    color: '#9ca3af',
    marginBottom: 32,
  },
  section: {
    marginBottom: 24,
  },
  inputGroup: {
    marginBottom: 12,
  },
  input: {
    width: '100%',
    padding: 12,
    marginTop: 8,
    background: '#2a2a2a',
    border: '1px solid #3a3a3a',
    borderRadius: 8,
    color: '#fff',
    fontSize: 14,
  },
  primaryButton: {
    width: '100%',
    padding: 14,
    background: '#3b82f6',
    border: 'none',
    borderRadius: 8,
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
    cursor: 'pointer',
    marginTop: 8,
  },
  secondaryButton: {
    width: '100%',
    padding: 14,
    background: '#10b981',
    border: 'none',
    borderRadius: 8,
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
    cursor: 'pointer',
  },
  info: {
    padding: 16,
    background: 'rgba(59, 130, 246, 0.1)',
    borderRadius: 8,
    color: '#9ca3af',
    fontSize: 13,
    lineHeight: 1.6,
  },
};
