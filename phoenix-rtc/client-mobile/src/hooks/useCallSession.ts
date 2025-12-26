import { useState, useCallback, useEffect } from 'react';
import axios from 'axios';
import { useWebSocket } from './useWebSocket';
import { useLiveKit } from './useLiveKit';
import { CallState, SessionType, TokenResponse, CallRequest, CallInvite, Participant } from '../types';

// 从环境变量或配置获取 API 地址
const API_BASE_URL = process.env.API_BASE_URL || 'http://localhost:8080/api/rtc';
const WS_URL = process.env.WS_URL || 'ws://localhost:8080/ws/rtc';

interface CallSessionHook {
  state: CallState;
  isAudioMuted: boolean;
  isVideoMuted: boolean;
  participants: Participant[];
  localParticipant?: Participant;
  error: string | null;
  isConnected: boolean;
  wsConnected: boolean;
  currentRoomName: string | null;
  incomingInvite: CallInvite | null;
  // 修正后的 API：拆分"发送邀请"和"连接媒体"
  sendInvite: (request: CallRequest) => Promise<string>;  // 返回 roomName
  connectMedia: () => Promise<void>;                      // 连接 LiveKit
  joinCall: (roomName: string) => Promise<void>;
  leaveCall: () => Promise<void>;
  acceptIncomingCall: () => Promise<void>;
  rejectIncomingCall: () => Promise<void>;
  toggleAudio: () => Promise<void>;
  toggleVideo: () => Promise<void>;
  clearError: () => void;
}

/**
 * 通话会话 Hook - 整合 WebSocket 和 LiveKit
 */
export const useCallSession = (): CallSessionHook => {
  const [incomingInvite, setIncomingInvite] = useState<CallInvite | null>(null);
  const [currentRoomName, setCurrentRoomName] = useState<string | null>(null);

  // WebSocket Hook (业务信令)
  const {
    isConnected: wsConnected,
    sendMessage,
    subscribeToInvite,
    error: wsError,
  } = useWebSocket({ url: WS_URL });

  // LiveKit Hook (媒体流)
  const liveKit = useLiveKit();

  // 组合错误
  const error = wsError || liveKit.error;

  // 订阅通话邀请
  useEffect(() => {
    subscribeToInvite((invite) => {
      console.log('收到通话邀请:', invite);
      setIncomingInvite(invite);
    });
  }, [subscribeToInvite]);

  // 保存临时的 Token 信息，用于后续连接媒体
  const [pendingToken, setPendingToken] = useState<TokenResponse | null>(null);

  /**
   * 发起通话 - 第一阶段：发送邀请
   * 修复：不再立即连接 LiveKit，仅发送信令
   */
  const sendInvite = useCallback(async (request: CallRequest): Promise<string> => {
    try {
      console.log('开始发起通话，发送邀请:', request);

      // 1. 调用服务端 API 创建房间（不生成 Token，或仅生成但不立即使用）
      const response = await axios.post(`${API_BASE_URL}/call/start`, request);
      const data: TokenResponse = response.data.data;

      // 2. 保存房间信息和 Token（暂存，等待对方接听后再连接）
      setCurrentRoomName(data.roomName);
      setPendingToken(data);

      // 3. 服务端会通过 WebSocket/IM 推送邀请给被叫
      // 这里可以发送信令通知被叫（如果使用 WebSocket 信令）
      if (wsConnected && sendMessage) {
        sendMessage({
          type: 'rtc',
          cmd: 'invite',
          data: {
            roomId: data.roomName,
            inviterId: request.inviterId,
            targetId: request.targetId,
            title: request.title,
            mode: request.sessionType
          }
        });
      }

      console.log('邀请已发送，等待对方接听... 房间:', data.roomName);
      return data.roomName;
    } catch (e) {
      console.error('发送邀请失败:', e);
      throw e;
    }
  }, [wsConnected, sendMessage]);

  /**
   * 连接媒体 - 第二阶段：在收到对方接受后连接 LiveKit
   * 修复：仅在需要时连接媒体，避免"抢跑"
   */
  const connectMedia = useCallback(async () => {
    if (!pendingToken) {
      throw new Error('没有可用的 Token，请先调用 sendInvite');
    }

    try {
      console.log('连接媒体服务器:', pendingToken.roomName);

      // 连接到 LiveKit
      await liveKit.connect(pendingToken.url, pendingToken.token);

      console.log('媒体连接成功');
    } catch (e) {
      console.error('连接媒体失败:', e);
      throw e;
    }
  }, [pendingToken, liveKit]);

  // 加入通话
  const joinCall = useCallback(async (roomName: string) => {
    try {
      console.log('加入通话:', roomName);

      // 1. 调用服务端 API 获取 Token
      const response = await axios.post(`${API_BASE_URL}/call/join`, { roomName });
      const data: TokenResponse = response.data.data;

      // 2. 保存房间信息
      setCurrentRoomName(data.roomName);

      // 3. 连接到 LiveKit
      await liveKit.connect(data.url, data.token);

      console.log('加入通话成功');
    } catch (e) {
      console.error('加入通话失败:', e);
      throw e;
    }
  }, [liveKit]);

  // 离开通话
  const leaveCall = useCallback(async () => {
    try {
      if (!currentRoomName) return;

      console.log('离开通话:', currentRoomName);

      // 1. 断开 LiveKit
      await liveKit.disconnect();

      // 2. 调用服务端 API
      await axios.post(`${API_BASE_URL}/call/leave`, { roomName: currentRoomName });

      // 3. 服务端会通过 WebSocket 通知其他用户

      // 4. 清理状态
      setCurrentRoomName(null);
      setIncomingInvite(null);

      console.log('离开通话成功');
    } catch (e) {
      console.error('离开通话失败:', e);
      throw e;
    }
  }, [currentRoomName, liveKit, sendMessage]);

  // 接听来电
  const acceptIncomingCall = useCallback(async () => {
    if (!incomingInvite) return;

    try {
      console.log('接听来电:', incomingInvite.roomId);

      // 1. 加入房间并获取 Token
      // joinCall 会连接 LiveKit（符合标准流程：被叫先连接）
      await joinCall(incomingInvite.roomId);

      // 2. 通过 WebSocket 通知发起方"已接受"
      if (wsConnected && sendMessage) {
        sendMessage({
          type: 'rtc',
          cmd: 'accept',
          data: {
            roomId: incomingInvite.roomId,
            acceptorId: 'current_user_id', // 需要从上下文获取
            // initiatorId: incomingInvite.inviterId // 如果需要
          }
        });
      }

      // 3. 清除来电邀请
      setIncomingInvite(null);

      console.log('接听成功，已连接媒体');
    } catch (e) {
      console.error('接听失败:', e);
      throw e;
    }
  }, [incomingInvite, joinCall, wsConnected, sendMessage]);

  // 拒绝来电
  const rejectIncomingCall = useCallback(() => {
    console.log('拒绝来电');
    setIncomingInvite(null);
  }, []);

  // 切换麦克风
  const toggleAudio = useCallback(async () => {
    await liveKit.toggleAudio();
  }, [liveKit]);

  // 切换摄像头
  const toggleVideo = useCallback(async () => {
    await liveKit.toggleVideo();
  }, [liveKit]);

  // 清除错误
  const clearError = useCallback(() => {
    // WebSocket 和 LiveKit 的错误在各自 hook 中处理
    // 这里可以添加额外的错误清理逻辑
  }, []);

  return {
    state: liveKit.state,
    isAudioMuted: liveKit.isAudioMuted,
    isVideoMuted: liveKit.isVideoMuted,
    participants: liveKit.participants,
    localParticipant: liveKit.localParticipant,
    error,
    isConnected: liveKit.state === CallState.CONNECTED,
    wsConnected,
    currentRoomName,
    incomingInvite,
    // 新的 API
    sendInvite,
    connectMedia,
    // 保留 joinCall 用于被叫接听
    joinCall,
    leaveCall,
    acceptIncomingCall,
    rejectIncomingCall,
    toggleAudio,
    toggleVideo,
    clearError,
  };
};
