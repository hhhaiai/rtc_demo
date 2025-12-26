package com.phoenix.rtc.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 监控指标配置
 * 基于 Micrometer + Prometheus
 */
@Configuration
@Slf4j
public class MetricsConfig {

    private final MeterRegistry meterRegistry;

    public MetricsConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    // 活跃通话数
    private final AtomicInteger activeCalls = new AtomicInteger(0);

    // 今日通话总数
    private final AtomicInteger totalCallsToday = new AtomicInteger(0);

    // 今日失败通话数
    private final AtomicInteger failedCallsToday = new AtomicInteger(0);

    /**
     * 活跃通话数 Gauge
     */
    @Bean
    public Gauge activeCallsGauge() {
        return Gauge.builder("rtc.calls.active", activeCalls, AtomicInteger::get)
                .description("当前活跃的通话数量")
                .register(meterRegistry);
    }

    /**
     * 今日通话总数 Counter
     */
    @Bean
    public Counter totalCallsCounter() {
        return Counter.builder("rtc.calls.total")
                .description("今日通话总数")
                .register(meterRegistry);
    }

    /**
     * 今日失败通话数 Counter
     */
    @Bean
    public Counter failedCallsCounter() {
        return Counter.builder("rtc.calls.failed")
                .description("今日失败通话数")
                .register(meterRegistry);
    }

    /**
     * 通话创建时间 Timer
     */
    @Bean
    public Timer callCreationTimer() {
        return Timer.builder("rtc.call.creation.time")
                .description("通话创建耗时")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    /**
     * Token 生成时间 Timer
     */
    @Bean
    public Timer tokenGenerationTimer() {
        return Timer.builder("rtc.token.generation.time")
                .description("Token生成耗时")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    /**
     * WebSocket 连接数 Gauge
     */
    @Bean
    public Gauge websocketConnectionsGauge() {
        AtomicInteger wsConnections = new AtomicInteger(0);
        return Gauge.builder("rtc.websocket.connections", wsConnections, AtomicInteger::get)
                .description("当前WebSocket连接数")
                .register(meterRegistry);
    }

    /**
     * 增加活跃通话数
     */
    public void incrementActiveCalls() {
        activeCalls.incrementAndGet();
    }

    /**
     * 减少活跃通话数
     */
    public void decrementActiveCalls() {
        activeCalls.decrementAndGet();
        if (activeCalls.get() < 0) {
            activeCalls.set(0);
        }
    }

    /**
     * 增加总通话数
     */
    public void incrementTotalCalls() {
        totalCallsToday.incrementAndGet();
        meterRegistry.counter("rtc.calls.total").increment();
    }

    /**
     * 增加失败通话数
     */
    public void incrementFailedCalls() {
        failedCallsToday.incrementAndGet();
        meterRegistry.counter("rtc.calls.failed").increment();
    }

    /**
     * 获取当前指标值
     */
    public MetricsSnapshot getMetricsSnapshot() {
        return new MetricsSnapshot(
            activeCalls.get(),
            totalCallsToday.get(),
            failedCallsToday.get()
        );
    }

    public static class MetricsSnapshot {
        private final int activeCalls;
        private final int totalCallsToday;
        private final int failedCallsToday;

        public MetricsSnapshot(int activeCalls, int totalCallsToday, int failedCallsToday) {
            this.activeCalls = activeCalls;
            this.totalCallsToday = totalCallsToday;
            this.failedCallsToday = failedCallsToday;
        }

        public int getActiveCalls() {
            return activeCalls;
        }

        public int getTotalCallsToday() {
            return totalCallsToday;
        }

        public int getFailedCallsToday() {
            return failedCallsToday;
        }

        public double getSuccessRate() {
            if (totalCallsToday == 0) return 0.0;
            return (double) (totalCallsToday - failedCallsToday) / totalCallsToday * 100;
        }
    }
}
