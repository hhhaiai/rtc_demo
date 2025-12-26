package com.phoenix.rtc.config;

import io.livekit.server.LiveKitServerClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * LiveKit 配置类
 * 修复: 移除硬编码默认值，强制使用环境变量
 */
@Configuration
@Data
@Slf4j
public class LiveKitConfig {

    @Value("${LIVEKIT_URL}")
    private String url;

    @Value("${LIVEKIT_API_KEY}")
    private String apiKey;

    @Value("${LIVEKIT_API_SECRET}")
    private String apiSecret;

    /**
     * 启动时检查 LiveKit 配置
     */
    @PostConstruct
    public void validateConfig() {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalStateException("LIVEKIT_URL 环境变量未配置，系统无法启动！");
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("LIVEKIT_API_KEY 环境变量未配置，系统无法启动！");
        }
        if (apiSecret == null || apiSecret.trim().isEmpty()) {
            throw new IllegalStateException("LIVEKIT_API_SECRET 环境变量未配置，系统无法启动！");
        }
        log.info("LiveKit 配置验证通过 - URL: {}", url);
    }

    @Bean
    public LiveKitServerClient liveKitServerClient() {
        return new LiveKitServerClient(url, apiKey, apiSecret);
    }
}
