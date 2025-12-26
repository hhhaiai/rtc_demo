import React, { useState, useEffect } from 'react';
import {
  View,
  StyleSheet,
  Text,
  TouchableOpacity,
  SafeAreaView,
  StatusBar,
  Alert,
  ActivityIndicator,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useCallSession } from '../hooks/useCallSession';
import { VideoGrid } from './VideoView';
import { SessionType } from '../types';

interface CallScreenProps {
  route?: {
    params?: {
      targetUserIds?: string[];
      sessionType?: SessionType;
      roomName?: string;
    };
  };
  navigation?: any;
}

/**
 * 通话主界面
 */
export const CallScreen: React.FC<CallScreenProps> = ({ route, navigation }) => {
  const {
    state,
    isAudioMuted,
    isVideoMuted,
    participants,
    localParticipant,
    error,
    isConnected,
    incomingInvite,
    startCall,
    joinCall,
    leaveCall,
    acceptIncomingCall,
    rejectIncomingCall,
    toggleAudio,
    toggleVideo,
  } = useCallSession();

  const [isLoading, setIsLoading] = useState(false);

  // 处理错误
  useEffect(() => {
    if (error) {
      Alert.alert('错误', error, [{ text: '确定' }]);
    }
  }, [error]);

  // 处理来电邀请
  useEffect(() => {
    if (incomingInvite) {
      Alert.alert(
        '来电',
        `${incomingInvite.inviterName} 邀请你${incomingInvite.mode === 'audio' ? '语音' : '视频'}通话`,
        [
          { text: '拒绝', style: 'destructive', onPress: rejectIncomingCall },
          { text: '接听', style: 'default', onPress: handleAccept },
        ]
      );
    }
  }, [incomingInvite]);

  // 发起通话
  const handleStartCall = async () => {
    if (!route?.params?.targetUserIds) {
      Alert.alert('提示', '请选择联系人');
      return;
    }

    setIsLoading(true);
    try {
      await startCall({
        targetUserIds: route.params.targetUserIds,
        sessionType: route.params.sessionType || SessionType.ONE_V_ONE,
        title: '通话',
      });
    } catch (e) {
      Alert.alert('错误', e.message || '发起通话失败');
    } finally {
      setIsLoading(false);
    }
  };

  // 加入通话
  const handleJoin = async () => {
    if (!route?.params?.roomName) {
      Alert.alert('提示', '房间号无效');
      return;
    }

    setIsLoading(true);
    try {
      await joinCall(route.params.roomName);
    } catch (e) {
      Alert.alert('错误', e.message || '加入通话失败');
    } finally {
      setIsLoading(false);
    }
  };

  // 接听来电
  const handleAccept = async () => {
    setIsLoading(true);
    try {
      await acceptIncomingCall();
    } catch (e) {
      Alert.alert('错误', e.message || '接听失败');
    } finally {
      setIsLoading(false);
    }
  };

  // 挂断/离开
  const handleLeave = async () => {
    setIsLoading(true);
    try {
      await leaveCall();
      navigation?.goBack();
    } catch (e) {
      Alert.alert('错误', e.message || '挂断失败');
    } finally {
      setIsLoading(false);
    }
  };

  // 渲染控制栏
  const renderControls = () => (
    <View style={styles.controlsContainer}>
      <TouchableOpacity
        style={[styles.controlButton, isAudioMuted && styles.controlButtonActive]}
        onPress={toggleAudio}
        disabled={!isConnected}
      >
        <Icon
          name={isAudioMuted ? 'microphone-off' : 'microphone'}
          size={28}
          color="#fff"
        />
        <Text style={styles.controlText}>
          {isAudioMuted ? '取消静音' : '静音'}
        </Text>
      </TouchableOpacity>

      <TouchableOpacity
        style={styles.hangUpButton}
        onPress={handleLeave}
        disabled={isLoading}
      >
        <Icon name="phone-hangup" size={32} color="#fff" />
        <Text style={styles.hangUpText}>挂断</Text>
      </TouchableOpacity>

      <TouchableOpacity
        style={[styles.controlButton, isVideoMuted && styles.controlButtonActive]}
        onPress={toggleVideo}
        disabled={!isConnected}
      >
        <Icon
          name={isVideoMuted ? 'video-off' : 'video'}
          size={28}
          color="#fff"
        />
        <Text style={styles.controlText}>
          {isVideoMuted ? '开启视频' : '关闭视频'}
        </Text>
      </TouchableOpacity>
    </View>
  );

  // 渲染状态栏
  const renderStatusBar = () => {
    let statusText = '';
    let statusColor = '#4ade80';

    switch (state) {
      case 'CONNECTING':
        statusText = '连接中...';
        statusColor = '#fbbf24';
        break;
      case 'CONNECTED':
        statusText = `已连接 (${participants.length + 1}人)`;
        statusColor = '#4ade80';
        break;
      case 'RECONNECTING':
        statusText = '重连中...';
        statusColor = '#f59e0b';
        break;
      case 'CALLING':
        statusText = '呼叫中...';
        statusColor = '#60a5fa';
        break;
      case 'RINGING':
        statusText = '来电中...';
        statusColor = '#60a5fa';
        break;
      default:
        statusText = '空闲';
        statusColor = '#9ca3af';
    }

    return (
      <View style={styles.statusBar}>
        <View style={[styles.statusDot, { backgroundColor: statusColor }]} />
        <Text style={styles.statusText}>{statusText}</Text>
        {isLoading && <ActivityIndicator size="small" color="#fff" style={styles.loadingIndicator} />}
      </View>
    );
  };

  // 渲染空状态
  const renderEmptyState = () => (
    <View style={styles.emptyContainer}>
      <Icon name="video" size={80} color="#4b5563" />
      <Text style={styles.emptyTitle}>准备就绪</Text>
      <Text style={styles.emptySubtitle}>
        {route?.params?.targetUserIds ? '点击下方按钮发起通话' : '等待来电或加入房间'}
      </Text>

      {route?.params?.targetUserIds && (
        <TouchableOpacity
          style={styles.startButton}
          onPress={handleStartCall}
          disabled={isLoading}
        >
          <Icon name="phone" size={24} color="#fff" />
          <Text style={styles.startButtonText}>
            {isLoading ? '呼叫中...' : `呼叫 ${route.params.targetUserIds.length} 位用户`}
          </Text>
        </TouchableOpacity>
      )}

      {route?.params?.roomName && (
        <TouchableOpacity
          style={styles.startButton}
          onPress={handleJoin}
          disabled={isLoading}
        >
          <Icon name="login" size={24} color="#fff" />
          <Text style={styles.startButtonText}>
            {isLoading ? '加入中...' : `加入房间 ${route.params.roomName}`}
          </Text>
        </TouchableOpacity>
      )}
    </View>
  );

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" />

      {/* 顶部状态栏 */}
      {renderStatusBar()}

      {/* 视频区域 */}
      <View style={styles.videoContainer}>
        {isConnected && (participants.length > 0 || localParticipant) ? (
          <VideoGrid
            participants={participants}
            localParticipant={localParticipant}
            isAudioMuted={isAudioMuted}
            isVideoMuted={isVideoMuted}
          />
        ) : (
          renderEmptyState()
        )}
      </View>

      {/* 控制栏 */}
      {renderControls()}
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
  },
  statusBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 8,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: 8,
  },
  statusText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '500',
  },
  loadingIndicator: {
    marginLeft: 8,
  },
  videoContainer: {
    flex: 1,
    backgroundColor: '#000',
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  emptyTitle: {
    color: '#fff',
    fontSize: 24,
    fontWeight: 'bold',
    marginTop: 16,
  },
  emptySubtitle: {
    color: '#9ca3af',
    fontSize: 14,
    marginTop: 8,
    textAlign: 'center',
  },
  startButton: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#3b82f6',
    paddingHorizontal: 24,
    paddingVertical: 14,
    borderRadius: 12,
    marginTop: 24,
    gap: 8,
  },
  startButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  controlsContainer: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'center',
    paddingHorizontal: 24,
    paddingVertical: 20,
    backgroundColor: '#111',
    borderTopWidth: 1,
    borderTopColor: '#333',
  },
  controlButton: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: '#374151',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 4,
  },
  controlButtonActive: {
    backgroundColor: '#ef4444',
  },
  controlText: {
    color: '#fff',
    fontSize: 10,
  },
  hangUpButton: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: '#ef4444',
    justifyContent: 'center',
    alignItems: 'center',
    marginHorizontal: 16,
  },
  hangUpText: {
    color: '#fff',
    fontSize: 10,
    marginTop: 2,
  },
});
