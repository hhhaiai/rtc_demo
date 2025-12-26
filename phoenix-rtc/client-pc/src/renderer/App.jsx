import React, { useState, useEffect } from 'react';
import { CallScreen } from './components/CallScreen';
import { HomeScreen } from './components/HomeScreen';
import { IncomingCallModal } from './components/IncomingCallModal';
import { useCallSession } from './hooks/useCallSession';

/**
 * Phoenix RTC 桌面应用主组件
 */
export default function App() {
  const [view, setView] = useState('home'); // home, call
  const [showIncoming, setShowIncoming] = useState(false);

  const {
    state,
    incomingInvite,
    acceptIncomingCall,
    rejectIncomingCall,
  } = useCallSession();

  // 监听来电
  useEffect(() => {
    if (incomingInvite) {
      setShowIncoming(true);

      // 桌面通知
      if (window.electronAPI) {
        window.electronAPI.showNotification(
          '来电',
          `${incomingInvite.inviterName} 邀请你${incomingInvite.mode === 'audio' ? '语音' : '视频'}通话`
        );
      }
    } else {
      setShowIncoming(false);
    }
  }, [incomingInvite]);

  // 处理接听
  const handleAccept = async () => {
    try {
      await acceptIncomingCall();
      setShowIncoming(false);
      setView('call');
    } catch (e) {
      console.error('接听失败:', e);
      alert('接听失败: ' + e.message);
    }
  };

  // 处理拒绝
  const handleReject = async () => {
    try {
      await rejectIncomingCall();
      setShowIncoming(false);
    } catch (e) {
      console.error('拒绝失败:', e);
    }
  };

  // 渲染当前视图
  const renderView = () => {
    switch (view) {
      case 'home':
        return <HomeScreen onStartCall={() => setView('call')} />;
      case 'call':
        return <CallScreen onLeave={() => setView('home')} />;
      default:
        return <HomeScreen onStartCall={() => setView('call')} />;
    }
  };

  return (
    <div style={styles.container}>
      {renderView()}

      {/* 来电弹窗 */}
      {showIncoming && incomingInvite && (
        <IncomingCallModal
          invite={incomingInvite}
          onAccept={handleAccept}
          onReject={handleReject}
        />
      )}
    </div>
  );
}

const styles = {
  container: {
    width: '100%',
    height: '100%',
    background: '#0a0a0a',
  },
};
