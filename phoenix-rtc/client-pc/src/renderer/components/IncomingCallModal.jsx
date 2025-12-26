import React from 'react';

/**
 * 来电弹窗 - 桌面端
 */
export const IncomingCallModal = ({ invite, onAccept, onReject }) => {
  const isVideo = invite.mode === 'video' || invite.mode === 'live';

  return (
    <div style={styles.overlay}>
      <div style={styles.modal}>
        <div style={styles.avatar}>📞</div>
        <h2 style={styles.title}>{invite.inviterName}</h2>
        <p style={styles.subtitle}>
          {isVideo ? '请求视频通话' : '请求语音通话'}
        </p>
        <p style={styles.roomInfo}>房间: {invite.roomId}</p>
        <div style={styles.buttons}>
          <button style={styles.rejectButton} onClick={onReject}>
            拒绝
          </button>
          <button style={styles.acceptButton} onClick={onAccept}>
            接听
          </button>
        </div>
      </div>
    </div>
  );
};

const styles = {
  overlay: {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    background: 'rgba(0, 0, 0, 0.8)',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: 1000,
  },
  modal: {
    background: '#1a1a1a',
    padding: 40,
    borderRadius: 16,
    width: 400,
    textAlign: 'center',
    border: '1px solid #333',
  },
  avatar: {
    fontSize: 64,
    marginBottom: 16,
  },
  title: {
    color: '#fff',
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 8,
  },
  subtitle: {
    color: '#9ca3af',
    fontSize: 16,
    marginBottom: 8,
  },
  roomInfo: {
    color: '#6b7280',
    fontSize: 14,
    marginBottom: 32,
  },
  buttons: {
    display: 'flex',
    gap: 16,
  },
  rejectButton: {
    flex: 1,
    padding: 16,
    background: '#ef4444',
    border: 'none',
    borderRadius: 10,
    color: '#fff',
    fontSize: 16,
    fontWeight: 'bold',
    cursor: 'pointer',
  },
  acceptButton: {
    flex: 1,
    padding: 16,
    background: '#10b981',
    border: 'none',
    borderRadius: 10,
    color: '#fff',
    fontSize: 16,
    fontWeight: 'bold',
    cursor: 'pointer',
  },
};
