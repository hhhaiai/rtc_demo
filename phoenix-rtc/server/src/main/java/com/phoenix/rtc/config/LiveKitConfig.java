package com.phoenix.rtc.config;

import io.livekit.server.LiveKitServerClient;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LiveKit 配置类
 */
@Configuration
@Data
public class LiveKitConfig {

    @Value("${livekit.url:ws://localhost:7880}")
    private String url;

    @Value("${livekit.api.key:devkey}")
    private String apiKey;

    @Value("${livekit.api.secret:secret}")
    private String apiSecret;

    @Bean
    public LiveKitServerClient liveKitServerClient() {
        return new LiveKitServerClient(url, apiKey, apiSecret);
    }
}
