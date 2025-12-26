import { useState, useCallback, useEffect } from 'react';
import axios from 'axios';
import { Room, RoomEvent } from 'livekit-client';

// 从环境变量或配置获取 API 地址
const API_BASE_URL = process.env.API_BASE_URL || 'http://localhost:8080/api/rtc';
const WS_URL = process.env.WS_URL || 'ws://localhost:8080/ws/rtc';

/**
 * 桌面端通话会话 Hook
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

  // 发起通话
  const startCall = useCallback(async (request) => {
    try {
      setState('CONNECTING');
      const response = await axios.post(`${API_BASE_URL}/call/start`, request);
      const data = response.data.data;

      setCurrentRoomName(data.roomName);

      // 服务端会通过 WebSocket 发送邀请

      // 连接 LiveKit
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
  }, [ws, initializeRoom]);

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
    startCall,
    joinCall,
    leaveCall,
    acceptIncomingCall,
    rejectIncomingCall,
    toggleAudio,
    toggleVideo,
  };
};
