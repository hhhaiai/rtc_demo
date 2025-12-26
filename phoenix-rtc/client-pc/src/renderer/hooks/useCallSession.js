import { useState, useCallback, useEffect } from 'react';
import axios from 'axios';
import { Room, RoomEvent } from 'livekit-client';

// 从环境变量或配置获取 API 地址
const API_BASE_URL = process.env.API_BASE_URL || 'http://localhost:8080/api/rtc';
const WS_URL = process.env.WS_URL || 'ws://localhost:8080/ws/rtc';

/**
 * 桌面端通话会话 Hook
 * 修复: 拆分"发送邀请"和"连接媒体"两个阶段，避免抢跑
 */
export const useCallSession = () => {
  const [room, setRoom] = useState(null);
  const [state, setState] = useState('IDLE');
  const [participants, setParticipants] = useState([]);
  const [localParticipant, setLocalParticipant] = useState(null);
  const [isAudioMuted, setIsAudioMuted] = useState(false);
  const [isVideoMuted, setIsVideoMuted] = useState(false);
  const [error, setError] = useState(null);
  const [incomingInvite, setIncomingInvite] = useState(null);
  const [currentRoomName, setCurrentRoomName] = useState(null);

  // 保存临时的 Token 信息，用于后续连接媒体
  const [pendingToken, setPendingToken] = useState(null);

  // WebSocket 连接（简化版，直接使用 fetch 轮询或原生 WebSocket）
  const [ws, setWs] = useState(null);

  // 初始化 WebSocket
  useEffect(() => {
    const websocket = new WebSocket(WS_URL);

    websocket.onopen = () => {
      console.log('WebSocket 已连接');
      setWs(websocket);
    };

    websocket.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data);
        if (message.type === 'rtc' && message.cmd === 'ringing') {
          setIncomingInvite({
            roomId: message.data.roomName,
            inviterId: message.data.inviterId,
            inviterName: message.data.inviterName,
            mode: message.data.mode,
            timestamp: message.timestamp,
          });
        }
      } catch (e) {
        console.error('解析消息失败:', e);
      }
    };

    websocket.onclose = () => {
      console.log('WebSocket 已断开');
      setWs(null);
    };

    return () => {
      websocket.close();
    };
  }, []);

  // 初始化 LiveKit 房间
  const initializeRoom = useCallback(() => {
    const newRoom = new Room({
      adaptiveStream: true,
      dynacast: true,
    });

    // 事件监听
    newRoom.on(RoomEvent.Connected, () => {
      setState('CONNECTED');
      setLocalParticipant(newRoom.localParticipant);
    });

    newRoom.on(RoomEvent.Disconnected, () => {
      setState('IDLE');
      setLocalParticipant(null);
      setParticipants([]);
    });

    newRoom.on(RoomEvent.ParticipantConnected, (participant) => {
      setParticipants((prev) => [...prev, participant]);
    });

    newRoom.on(RoomEvent.ParticipantDisconnected, (participant) => {
      setParticipants((prev) => prev.filter((p) => p.identity !== participant.identity));
    });

    newRoom.on(RoomEvent.TrackSubscribed, () => {
      setParticipants(Array.from(newRoom.participants.values()));
    });

    return newRoom;
  }, []);

  /**
   * 发起通话 - 第一阶段：发送邀请
   * 修复：不再立即连接 LiveKit，仅发送信令
   */
  const sendInvite = useCallback(async (request) => {
    try {
      setState('CALLING');
      console.log('开始发起通话，发送邀请:', request);

      // 1. 调用服务端 API 创建房间
      const response = await axios.post(`${API_BASE_URL}/call/start`, request);
      const data = response.data.data;

      // 2. 保存房间信息和 Token（暂存，等待对方接听后再连接）
      setCurrentRoomName(data.roomName);
      setPendingToken(data);

      // 3. 服务端会通过 WebSocket 推送邀请给被叫
      console.log('邀请已发送，等待对方接听... 房间:', data.roomName);
      return data.roomName;
    } catch (e) {
      console.error('发送邀请失败:', e);
      setError(e.message);
      setState('IDLE');
      throw e;
    }
  }, []);

  /**
   * 连接媒体 - 第二阶段：在收到对方接受后连接 LiveKit
   * 修复：仅在需要时连接媒体，避免"抢跑"
   */
  const connectMedia = useCallback(async () => {
    if (!pendingToken) {
      throw new Error('没有可用的 Token，请先调用 sendInvite');
    }

    try {
      setState('CONNECTING');
      console.log('连接媒体服务器:', pendingToken.roomName);

      // 连接 LiveKit
      const roomInstance = initializeRoom();
      await roomInstance.connect(pendingToken.url, pendingToken.token);
      await roomInstance.localParticipant.setMicrophoneEnabled(true);
      await roomInstance.localParticipant.setCameraEnabled(true);
      setRoom(roomInstance);

      console.log('媒体连接成功');
    } catch (e) {
      console.error('连接媒体失败:', e);
      setError(e.message);
      setState('IDLE');
      throw e;
    }
  }, [pendingToken, initializeRoom]);

  // 向后兼容：保留 startCall 但标记为废弃
  const startCall = useCallback(async (request) => {
    console.warn('startCall 已废弃，请使用 sendInvite + connectMedia 组合');
    const roomName = await sendInvite(request);
    await connectMedia();
    return roomName;
  }, [sendInvite, connectMedia]);

  // 加入通话
  const joinCall = useCallback(async (roomName) => {
    try {
      setState('CONNECTING');
      const response = await axios.post(`${API_BASE_URL}/call/join`, { roomName });
      const data = response.data.data;

      setCurrentRoomName(data.roomName);

      const roomInstance = initializeRoom();
      await roomInstance.connect(data.url, data.token);
      await roomInstance.localParticipant.setMicrophoneEnabled(true);
      await roomInstance.localParticipant.setCameraEnabled(true);
      setRoom(roomInstance);

    } catch (e) {
      setError(e.message);
      setState('IDLE');
      throw e;
    }
  }, [initializeRoom]);

  // 离开通话
  const leaveCall = useCallback(async () => {
    try {
      if (room) {
        await room.disconnect();
        setRoom(null);
      }

      if (currentRoomName) {
        await axios.post(`${API_BASE_URL}/call/leave`, { roomName: currentRoomName });

        // 服务端会通过 WebSocket 通知其他用户
      }

      setState('IDLE');
      setCurrentRoomName(null);
      setParticipants([]);
      setLocalParticipant(null);
    } catch (e) {
      setError(e.message);
      throw e;
    }
  }, [room, ws, currentRoomName]);

  // 接听来电
  const acceptIncomingCall = useCallback(async () => {
    if (!incomingInvite) return;

    try {
      await joinCall(incomingInvite.roomId);

      // 服务端会通过 WebSocket 通知发起方

      setIncomingInvite(null);
    } catch (e) {
      setError(e.message);
      throw e;
    }
  }, [incomingInvite, joinCall, ws]);

  // 拒绝来电
  const rejectIncomingCall = useCallback(() => {
    setIncomingInvite(null);
  }, []);

  // 切换麦克风
  const toggleAudio = useCallback(async () => {
    if (room?.localParticipant) {
      const enabled = room.localParticipant.isMicrophoneEnabled;
      await room.localParticipant.setMicrophoneEnabled(!enabled);
      setIsAudioMuted(!enabled);
    }
  }, [room]);

  // 切换摄像头
  const toggleVideo = useCallback(async () => {
    if (room?.localParticipant) {
      const enabled = room.localParticipant.isCameraEnabled;
      await room.localParticipant.setCameraEnabled(!enabled);
      setIsVideoMuted(!enabled);
    }
  }, [room]);

  return {
    state,
    participants,
    localParticipant,
    isAudioMuted,
    isVideoMuted,
    error,
    incomingInvite,
    currentRoomName,
    // 新的 API
    sendInvite,
    connectMedia,
    // 保留旧 API（已标记废弃）
    startCall,
    joinCall,
    leaveCall,
    acceptIncomingCall,
    rejectIncomingCall,
    toggleAudio,
    toggleVideo,
  };
};
