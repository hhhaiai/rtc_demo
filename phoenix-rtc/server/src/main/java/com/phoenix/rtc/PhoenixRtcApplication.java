package com.phoenix.rtc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Phoenix RTC 音视频服务主应用
 *
 * @author Phoenix RTC Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class PhoenixRtcApplication {

    public static void main(String[] args) {
        SpringApplication.run(PhoenixRtcApplication.class, args);
        System.out.println("=================================");
        System.out.println("Phoenix RTC Server Started!");
        System.out.println("=================================");
    }
}
