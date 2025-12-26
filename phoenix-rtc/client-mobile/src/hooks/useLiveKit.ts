import { useEffect, useState, useRef, useCallback } from 'react';
import { Room, RoomEvent, Participant, Track, VideoQuality } from 'livekit-client';
import { CallState } from '../types';

interface LiveKitHook {
  room: Room | null;
  state: CallState;
  participants: Participant[];
  localParticipant: Participant | null;
  isAudioMuted: boolean;
  isVideoMuted: boolean;
  error: string | null;
  connect: (url: string, token: string) => Promise<void>;
  disconnect: () => Promise<void>;
  toggleAudio: () => Promise<void>;
  toggleVideo: () => Promise<void>;
  switchCamera: () => Promise<void>;
}

/**
 * LiveKit Hook - 处理音视频连接和媒体流
 */
export const useLiveKit = (): LiveKitHook => {
  const [room, setRoom] = useState<Room | null>(null);
  const [state, setState] = useState<CallState>(CallState.IDLE);
  const [participants, setParticipants] = useState<Participant[]>([]);
  const [localParticipant, setLocalParticipant] = useState<Participant | null>(null);
  const [isAudioMuted, setIsAudioMuted] = useState(false);
  const [isVideoMuted, setIsVideoMuted] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const roomRef = useRef<Room | null>(null);

  // 初始化房间
  const initializeRoom = useCallback(() => {
    const newRoom = new Room({
      adaptiveStream: true,
      dynacast: true,
      videoCaptureDefaults: {
        resolution: {
          width: 1280,
          height: 720,
          frameRate: 30,
        },
      },
      audioCaptureDefaults: {
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
      },
    });

    // 设置事件监听器
    setupEventListeners(newRoom);

    roomRef.current = newRoom;
    setRoom(newRoom);

    return newRoom;
  }, []);

  // 设置事件监听器
  const setupEventListeners = useCallback((roomInstance: Room) => {
    // 连接状态变化
    roomInstance.on(RoomEvent.Connected, () => {
      console.log('LiveKit 已连接');
      setState(CallState.CONNECTED);
      setLocalParticipant(roomInstance.localParticipant);
    });

    roomInstance.on(RoomEvent.Disconnected, () => {
      console.log('LiveKit 已断开');
      setState(CallState.IDLE);
      setLocalParticipant(null);
      setParticipants([]);
    });

    roomInstance.on(RoomEvent.Reconnecting, () => {
      console.log('LiveKit 重连中...');
      setState(CallState.RECONNECTING);
    });

    roomInstance.on(RoomEvent.Reconnected, () => {
      console.log('LiveKit 重连成功');
      setState(CallState.CONNECTED);
    });

    // 参与者变化
    roomInstance.on(RoomEvent.ParticipantConnected, (participant: Participant) => {
      console.log('参与者加入:', participant.identity);
      setParticipants((prev) => [...prev, participant]);
    });

    roomInstance.on(RoomEvent.ParticipantDisconnected, (participant: Participant) => {
      console.log('参与者离开:', participant.identity);
      setParticipants((prev) => prev.filter((p) => p.identity !== participant.identity));
    });

    // 音视频轨道变化
    roomInstance.on(RoomEvent.TrackSubscribed, (track, publication, participant) => {
      console.log('轨道订阅:', track.sid, participant.identity);
      updateParticipants(roomInstance);
    });

    roomInstance.on(RoomEvent.TrackUnsubscribed, (track, publication, participant) => {
      console.log('轨道取消订阅:', track.sid, participant.identity);
      updateParticipants(roomInstance);
    });

    // 说话检测
    roomInstance.on(RoomEvent.AudioLevelChanged, (speakers) => {
      updateParticipants(roomInstance);
    });

    // 质量变化
    roomInstance.on(RoomEvent.ConnectionQualityChanged, (quality, participant) => {
      console.log(`连接质量变化 - ${participant.identity}: ${quality}`);
    });
  }, []);

  // 更新参与者列表
  const updateParticipants = useCallback((roomInstance: Room) => {
    const allParticipants = Array.from(roomInstance.participants.values());
    setParticipants(allParticipants);
    setLocalParticipant(roomInstance.localParticipant);
  }, []);

  // 连接
  const connect = useCallback(async (url: string, token: string) => {
    try {
      setState(CallState.CONNECTING);
      setError(null);

      let roomInstance = roomRef.current;
      if (!roomInstance) {
        roomInstance = initializeRoom();
      }

      await roomInstance.connect(url, token);

      // 发布本地音视频
      await roomInstance.localParticipant.setMicrophoneEnabled(true);
      await roomInstance.localParticipant.setCameraEnabled(true);

      console.log('成功连接到 LiveKit');
    } catch (e) {
      console.error('连接失败:', e);
      setError(e instanceof Error ? e.message : '连接失败');
      setState(CallState.IDLE);
    }
  }, [initializeRoom]);

  // 断开连接
  const disconnect = useCallback(async () => {
    try {
      setState(CallState.DISCONNECTING);

      if (roomRef.current) {
        await roomRef.current.disconnect();
        roomRef.current = null;
        setRoom(null);
      }

      setState(CallState.IDLE);
      setParticipants([]);
      setLocalParticipant(null);
      setIsAudioMuted(false);
      setIsVideoMuted(false);

      console.log('已断开连接');
    } catch (e) {
      console.error('断开连接失败:', e);
      setError(e instanceof Error ? e.message : '断开失败');
    }
  }, []);

  // 切换麦克风
  const toggleAudio = useCallback(async () => {
    if (!roomRef.current?.localParticipant) return;

    try {
      const current = roomRef.current.localParticipant.isMicrophoneEnabled;
      await roomRef.current.localParticipant.setMicrophoneEnabled(!current);
      setIsAudioMuted(!current);
      console.log('麦克风状态:', !current ? '静音' : '开启');
    } catch (e) {
      console.error('切换麦克风失败:', e);
      setError(e instanceof Error ? e.message : '切换麦克风失败');
    }
  }, []);

  // 切换摄像头
  const toggleVideo = useCallback(async () => {
    if (!roomRef.current?.localParticipant) return;

    try {
      const current = roomRef.current.localParticipant.isCameraEnabled;
      await roomRef.current.localParticipant.setCameraEnabled(!current);
      setIsVideoMuted(!current);
      console.log('摄像头状态:', !current ? '关闭' : '开启');
    } catch (e) {
      console.error('切换摄像头失败:', e);
      setError(e instanceof Error ? e.message : '切换摄像头失败');
    }
  }, []);

  // 切换摄像头 (前后)
  const switchCamera = useCallback(async () => {
    if (!roomRef.current?.localParticipant) return;

    try {
      const tracks = roomRef.current.localParticipant.getTrack(Track.Source.Camera);
      if (tracks && tracks.length > 0) {
        // 切换摄像头设备
        // 注意：需要设备管理器支持
        console.log('切换摄像头设备');
      }
    } catch (e) {
      console.error('切换摄像头失败:', e);
      setError(e instanceof Error ? e.message : '切换摄像头失败');
    }
  }, []);

  // 清理资源
  useEffect(() => {
    return () => {
      if (roomRef.current) {
        roomRef.current.disconnect();
      }
    };
  }, []);

  return {
    room,
    state,
    participants,
    localParticipant,
    isAudioMuted,
    isVideoMuted,
    error,
    connect,
    disconnect,
    toggleAudio,
    toggleVideo,
    switchCamera,
  };
};
