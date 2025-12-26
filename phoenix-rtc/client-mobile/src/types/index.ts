/**
 * Phoenix RTC 移动端类型定义
 */

// 通话状态
export enum CallState {
  IDLE = 'IDLE',
  CALLING = 'CALLING',
  RINGING = 'RINGING',
  CONNECTING = 'CONNECTING',
  CONNECTED = 'CONNECTED',
  RECONNECTING = 'RECONNECTING',
  DISCONNECTING = 'DISCONNECTING',
  MUTED_AUDIO = 'MUTED_AUDIO',
  MUTED_VIDEO = 'MUTED_VIDEO',
}

// 通话类型
export enum SessionType {
  ONE_V_ONE = 'video',
  AUDIO = 'audio',
  LIVE = 'live',
  GROUP = 'group',
}

// Token 响应
export interface TokenResponse {
  url: string;
  token: string;
  roomName: string;
  roomTitle?: string;
  expiresAt: number;
}

// WebSocket 消息
export interface WSMessage {
  type: 'rtc' | 'message' | 'state';
  cmd?: string;
  data?: any;
  timestamp?: number;
}

// 通话请求
export interface CallRequest {
  targetUserIds: string[];
  sessionType: SessionType;
  title?: string;
  isGroup?: boolean;
  maxParticipants?: number;
}

// 参与者信息
export interface Participant {
  id: string;
  name: string;
  isLocal: boolean;
  isSpeaking: boolean;
  videoTracks: any[];
  audioTracks: any[];
}

// 通话状态
export interface CallSession {
  state: CallState;
  roomName?: string;
  roomTitle?: string;
  token?: string;
  url?: string;
  participants: Participant[];
  localParticipant?: Participant;
  isAudioMuted: boolean;
  isVideoMuted: boolean;
  error?: string;
}

// WebSocket 配置
export interface WebSocketConfig {
  url: string;
  reconnectAttempts: number;
  reconnectInterval: number;
  heartbeatInterval: number;
}

// 呼叫邀请
export interface CallInvite {
  roomId: string;
  inviterId: string;
  inviterName: string;
  mode: SessionType;
  timestamp: number;
}
