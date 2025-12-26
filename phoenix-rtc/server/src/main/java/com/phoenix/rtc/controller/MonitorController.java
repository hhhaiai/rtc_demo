package com.phoenix.rtc.controller;

import com.phoenix.rtc.config.MetricsConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 监控控制器
 * 提供系统监控指标
 */
@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
@Slf4j
public class MonitorController {

    private final MetricsConfig metricsConfig;

    /**
     * 获取系统健康状态
     * GET /api/monitor/health
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "timestamp", System.currentTimeMillis(),
            "service", "phoenix-rtc"
        ));
    }

    /**
     * 获取监控指标
     * GET /api/monitor/metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<?> metrics() {
        MetricsConfig.MetricsSnapshot snapshot = metricsConfig.getMetricsSnapshot();

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", Map.of(
                "activeCalls", snapshot.getActiveCalls(),
                "totalCallsToday", snapshot.getTotalCallsToday(),
                "failedCallsToday", snapshot.getFailedCallsToday(),
                "successRate", String.format("%.2f%%", snapshot.getSuccessRate())
            )
        ));
    }

    /**
     * 获取系统信息
     * GET /api/monitor/info
     */
    @GetMapping("/info")
    public ResponseEntity<?> info() {
        Runtime runtime = Runtime.getRuntime();

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", Map.of(
                "service", "Phoenix RTC Server",
                "version", "1.0.0",
                "javaVersion", System.getProperty("java.version"),
                "memory", Map.of(
                    "total", runtime.totalMemory() / 1024 / 1024 + " MB",
                    "free", runtime.freeMemory() / 1024 / 1024 + " MB",
                    "max", runtime.maxMemory() / 1024 / 1024 + " MB",
                    "used", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024 + " MB"
                ),
                "processors", runtime.availableProcessors(),
                "currentTime", System.currentTimeMillis()
            )
        ));
    }
}
