import { useEffect, useRef, useState, useCallback } from 'react';
import { WSMessage, WebSocketConfig, CallInvite } from '../types';

interface WebSocketHook {
  isConnected: boolean;
  lastMessage: WSMessage | null;
  error: string | null;
  connect: (url: string) => void;
  disconnect: () => void;
  sendMessage: (message: WSMessage) => void;
  subscribeToInvite: (callback: (invite: CallInvite) => void) => void;
}

const DEFAULT_CONFIG: WebSocketConfig = {
  url: 'ws://localhost:8080/ws/rtc',
  reconnectAttempts: 5,
  reconnectInterval: 3000,
  heartbeatInterval: 30000,
};

/**
 * WebSocket Hook - 处理信令连接
 */
export const useWebSocket = (config: Partial<WebSocketConfig> = {}): WebSocketHook => {
  const mergedConfig = { ...DEFAULT_CONFIG, ...config };

  const [isConnected, setIsConnected] = useState(false);
  const [lastMessage, setLastMessage] = useState<WSMessage | null>(null);
  const [error, setError] = useState<string | null>(null);

  const ws = useRef<WebSocket | null>(null);
  const reconnectTimeout = useRef<NodeJS.Timeout | null>(null);
  const heartbeatInterval = useRef<NodeJS.Timeout | null>(null);
  const inviteCallback = useRef<(invite: CallInvite) => void>(() => {});

  // 心跳机制
  const startHeartbeat = useCallback(() => {
    if (heartbeatInterval.current) {
      clearInterval(heartbeatInterval.current);
    }

 heartbeatInterval.current = setInterval(() => {
      if (ws.current?.readyState === WebSocket.OPEN) {
        ws.current.send(JSON.stringify({ type: 'state', cmd: 'heartbeat', timestamp: Date.now() }));
      }
    }, mergedConfig.heartbeatInterval);
  }, [mergedConfig.heartbeatInterval]);

  // 停止心跳
  const stopHeartbeat = useCallback(() => {
    if (heartbeatInterval.current) {
      clearInterval(heartbeatInterval.current);
      heartbeatInterval.current = null;
    }
  }, []);

  // 重连机制
  const scheduleReconnect = useCallback(() => {
    if (reconnectTimeout.current) {
      clearTimeout(reconnectTimeout.current);
    }

    reconnectTimeout.current = setTimeout(() => {
      if (!isConnected && ws.current === null) {
        console.log('尝试重连...');
        connect(mergedConfig.url);
      }
    }, mergedConfig.reconnectInterval);
  }, [isConnected, mergedConfig]);

  // 处理接收到的消息
  const handleMessage = useCallback((event: MessageEvent) => {
    try {
      const message: WSMessage = JSON.parse(event.data);
      setLastMessage(message);

      // 处理通话邀请
      if (message.type === 'rtc' && message.cmd === 'ringing') {
        const invite: CallInvite = {
          roomId: message.data.roomName,
          inviterId: message.data.inviterId,
          inviterName: message.data.inviterName,
          mode: message.data.mode,
          timestamp: message.timestamp || Date.now(),
        };
        inviteCallback.current(invite);
      }

      console.log('收到消息:', message);
    } catch (e) {
      console.error('解析消息失败:', e);
      setError('消息解析失败');
    }
  }, []);

  // 连接 WebSocket
  const connect = useCallback((url: string) => {
    if (ws.current) {
      console.warn('WebSocket 已连接');
      return;
    }

    try {
      console.log('连接 WebSocket:', url);
      ws.current = new WebSocket(url);

      ws.current.onopen = () => {
        console.log('WebSocket 连接成功');
        setIsConnected(true);
        setError(null);
        startHeartbeat();
      };

      ws.current.onclose = (event) => {
        console.log('WebSocket 连接关闭:', event.code, event.reason);
        setIsConnected(false);
        ws.current = null;
        stopHeartbeat();
        scheduleReconnect();
      };

      ws.current.onerror = (event) => {
        console.error('WebSocket 错误:', event);
        setError('连接错误');
      };

      ws.current.onmessage = handleMessage;

    } catch (e) {
      console.error('创建 WebSocket 失败:', e);
      setError('无法创建连接');
    }
  }, [handleMessage, startHeartbeat, stopHeartbeat, scheduleReconnect]);

  // 断开连接
  const disconnect = useCallback(() => {
    if (reconnectTimeout.current) {
      clearTimeout(reconnectTimeout.current);
    }

    stopHeartbeat();

    if (ws.current) {
      ws.current.close(1000, '主动断开');
      ws.current = null;
    }

    setIsConnected(false);
    setError(null);
  }, [stopHeartbeat]);

  // 发送消息
  const sendMessage = useCallback((message: WSMessage) => {
    if (!ws.current || ws.current.readyState !== WebSocket.OPEN) {
      console.error('WebSocket 未连接，无法发送消息');
      return;
    }

    try {
      ws.current.send(JSON.stringify(message));
      console.log('发送消息:', message);
    } catch (e) {
      console.error('发送消息失败:', e);
      setError('发送消息失败');
    }
  }, []);

  // 订阅通话邀请
  const subscribeToInvite = useCallback((callback: (invite: CallInvite) => void) => {
    inviteCallback.current = callback;
  }, []);

  // 清理资源
  useEffect(() => {
    return () => {
      disconnect();
    };
  }, [disconnect]);

  return {
    isConnected,
    lastMessage,
    error,
    connect,
    disconnect,
    sendMessage,
    subscribeToInvite,
  };
};
