import React, { useState, useEffect } from 'react';
import { useCallSession } from '../hooks/useCallSession';

/**
 * 通话界面 - 桌面端
 */
export const CallScreen = ({ onLeave }) => {
  const {
    state,
    participants,
    localParticipant,
    isAudioMuted,
    isVideoMuted,
    error,
    leaveCall,
    toggleAudio,
    toggleVideo,
  } = useCallSession();

  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (error) {
      alert('错误: ' + error);
    }
  }, [error]);

  const handleLeave = async () => {
    setIsLoading(true);
    try {
      await leaveCall();
      onLeave();
    } catch (e) {
      alert('离开失败: ' + e.message);
    } finally {
      setIsLoading(false);
    }
  };

  const allParticipants = [...participants];
  if (localParticipant && !allParticipants.find(p => p.identity === localParticipant.identity)) {
    allParticipants.unshift(localParticipant);
  }

  return (
    <div style={styles.container}>
      <div style={styles.statusBar}>
        <div style={styles.statusIndicator}></div>
        <span style={styles.statusText}>
          {state === 'CONNECTED' ? `已连接 (${allParticipants.length}人)` : state}
        </span>
      </div>

      <div style={styles.videoArea}>
        {state === 'CONNECTED' && allParticipants.length > 0 ? (
          <div style={styles.videoGrid}>
            {allParticipants.map((participant, index) => (
              <div
                key={participant.identity}
                style={[
                  styles.videoTile,
                  allParticipants.length === 1 && styles.singleTile,
                  allParticipants.length === 2 && styles.doubleTile,
                  allParticipants.length <= 4 && styles.quadTile,
                ]}
              >
                <div style={styles.videoPlaceholder}>
                  <span style={styles.videoAvatar}>
                    {(participant.name || participant.identity).substring(0, 2).toUpperCase()}
                  </span>
                </div>
                <div style={styles.videoInfo}>
                  <span>
                    {participant.identity === localParticipant?.identity ? '我' : (participant.name || participant.identity)}
                  </span>
                  {participant.identity === localParticipant?.identity && isAudioMuted && (
                    <span>🔇</span>
                  )}
                </div>
                {participant.identity === localParticipant?.identity && (
                  <div style={styles.localBadge}>预览</div>
                )}
              </div>
            ))}
          </div>
        ) : (
          <div style={styles.emptyState}>
            <div style={styles.emptyIcon}>📹</div>
            <h2 style={styles.emptyTitle}>准备就绪</h2>
            <p style={styles.emptySubtitle}>等待连接...</p>
          </div>
        )}
      </div>

      <div style={styles.controls}>
        <button
          style={[styles.controlButton, isAudioMuted && styles.activeControl]}
          onClick={toggleAudio}
          disabled={state !== 'CONNECTED'}
        >
          {isAudioMuted ? '取消静音' : '静音'}
        </button>

        <button style={styles.hangUpButton} onClick={handleLeave} disabled={isLoading}>
          {isLoading ? '挂断中...' : '挂断'}
        </button>

        <button
          style={[styles.controlButton, isVideoMuted && styles.activeControl]}
          onClick={toggleVideo}
          disabled={state !== 'CONNECTED'}
        >
          {isVideoMuted ? '开启视频' : '关闭视频'}
        </button>
      </div>
    </div>
  );
};

const styles = {
  container: {
    width: '100%',
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
    background: '#000',
  },
  statusBar: {
    display: 'flex',
    alignItems: 'center',
    padding: '12px 20px',
    background: 'rgba(0, 0, 0, 0.7)',
    gap: 8,
  },
  statusIndicator: {
    width: 8,
    height: 8,
    borderRadius: 4,
    background: '#4ade80',
  },
  statusText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: 500,
  },
  videoArea: {
    flex: 1,
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    background: '#000',
  },
  videoGrid: {
    width: '100%',
    height: '100%',
    display: 'flex',
    flexWrap: 'wrap',
    gap: 4,
    padding: 4,
  },
  videoTile: {
    background: '#1a1a1a',
    borderRadius: 8,
    overflow: 'hidden',
    position: 'relative',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    flex: 1,
    minWidth: 200,
    minHeight: 150,
  },
  singleTile: {
    flex: 1,
  },
  doubleTile: {
    flex: '0 0 calc(50% - 2px)',
    height: '100%',
  },
  quadTile: {
    flex: '0 0 calc(50% - 2px)',
    height: '50%',
  },
  videoPlaceholder: {
    width: '100%',
    height: '100%',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    background: '#2d2d2d',
  },
  videoAvatar: {
    fontSize: 48,
    fontWeight: 'bold',
    color: '#fff',
  },
  videoInfo: {
    position: 'absolute',
    bottom: 8,
    left: 8,
    right: 8,
    background: 'rgba(0, 0, 0, 0.6)',
    padding: '6px 12px',
    borderRadius: 12,
    color: '#fff',
    fontSize: 12,
    display: 'flex',
    justifyContent: 'space-between',
  },
  localBadge: {
    position: 'absolute',
    top: 8,
    right: 8,
    background: 'rgba(59, 130, 246, 0.9)',
    padding: '4px 8px',
    borderRadius: 6,
    color: '#fff',
    fontSize: 10,
    fontWeight: 600,
  },
  emptyState: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: 12,
  },
  emptyIcon: {
    fontSize: 64,
  },
  emptyTitle: {
    color: '#fff',
    fontSize: 24,
    fontWeight: 'bold',
  },
  emptySubtitle: {
    color: '#9ca3af',
    fontSize: 14,
  },
  controls: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 16,
    padding: 20,
    background: '#111',
    borderTop: '1px solid #333',
  },
  controlButton: {
    width: 64,
    height: 64,
    borderRadius: 32,
    background: '#374151',
    border: 'none',
    color: '#fff',
    fontSize: 10,
    cursor: 'pointer',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
  },
  activeControl: {
    background: '#ef4444',
  },
  hangUpButton: {
    width: 72,
    height: 72,
    borderRadius: 36,
    background: '#ef4444',
    border: 'none',
    color: '#fff',
    fontSize: 12,
    fontWeight: 'bold',
    cursor: 'pointer',
  },
};
