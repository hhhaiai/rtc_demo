package com.phoenix.rtc.controller;

import com.phoenix.rtc.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

/**
 * WebSocket 订阅控制器
 *
 * 修复说明:
 * 1. 删除了所有 @MessageMapping 方法（死代码）
 *    - 原 WebSocketController.handleSignaling 逻辑已由 REST API RtcController 替代
 *    - 客户端应使用 REST API 而非 WebSocket 信令
 *
 * 2. 保留 SubscribeMapping 用于房间订阅（可选功能）
 *    - 用于客户端订阅房间主题接收广播消息
 *    - 依赖 WebSocketService 作为通知机制
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {

    private final WebSocketService webSocketService;

    /**
     * 订阅房间主题时的处理
     * 客户端订阅 /topic/room/{roomName} 后触发
     */
    @SubscribeMapping("/topic/room/{roomName}")
    public void onSubscribeRoom(String roomName) {
        log.info("用户订阅房间主题: {}", roomName);
        // 可以在这里发送房间当前状态给订阅者
    }
}
