package com.phoenix.rtc.config;

import com.phoenix.rtc.service.WebSocketService;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置
 * 支持 STOMP 协议，复用现有 WebSocket 连接处理 RTC 信令
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单消息代理，向客户端推送消息
        config.enableSimpleBroker("/topic", "/queue");
        // 设置应用前缀
        config.setApplicationDestinationPrefixes("/app");
        // 用户消息前缀
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket 端点，支持 SockJS 降级
        registry.addEndpoint("/ws/rtc")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
