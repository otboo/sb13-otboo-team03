package com.otboo.team03.notification.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class NotificationMetrics {

    static final String NOTIFICATIONS_CREATED = "notifications_created_total";
    static final String NOTIFICATIONS_DELIVERED = "notifications_delivered_total";
    static final String REALTIME_DELIVERY_FAILURE = "realtime_delivery_failure_total";
    static final String KAFKA_CONSUMER_FAILURE = "kafka_consumer_failure_total";
    static final String ACTIVE_SSE_CONNECTIONS = "active_sse_connections";
    static final String ACTIVE_WEBSOCKET_SESSIONS = "active_websocket_sessions";

    private final Counter notificationsCreated;
    private final Counter notificationsDelivered;
    private final Counter realtimeDeliveryFailure;
    private final Counter kafkaConsumerFailure;
    private final AtomicInteger activeSseConnections = new AtomicInteger();
    private final AtomicInteger activeWebSocketSessions = new AtomicInteger();

    public NotificationMetrics(MeterRegistry registry) {
        notificationsCreated = Counter.builder(NOTIFICATIONS_CREATED)
                .description("알림 DB 저장 성공 횟수")
                .register(registry);
        notificationsDelivered = Counter.builder(NOTIFICATIONS_DELIVERED)
                .description("실시간 알림 전달 성공 횟수")
                .register(registry);
        realtimeDeliveryFailure = Counter.builder(REALTIME_DELIVERY_FAILURE)
                .description("실시간 알림 전달 실패 횟수")
                .register(registry);
        kafkaConsumerFailure = Counter.builder(KAFKA_CONSUMER_FAILURE)
                .description("Kafka Consumer 처리 실패 횟수")
                .register(registry);

        Gauge.builder(ACTIVE_SSE_CONNECTIONS, activeSseConnections, AtomicInteger::get)
                .description("현재 활성화된 SSE 연결 수")
                .register(registry);
        Gauge.builder(ACTIVE_WEBSOCKET_SESSIONS, activeWebSocketSessions, AtomicInteger::get)
                .description("현재 활성화된 WebSocket 세션 수")
                .register(registry);
    }

    public void notificationCreated() {
        notificationsCreated.increment();
    }

    public void notificationDelivered() {
        notificationsDelivered.increment();
    }

    public void realtimeDeliveryFailed() {
        realtimeDeliveryFailure.increment();
    }

    public void kafkaConsumerFailed() {
        kafkaConsumerFailure.increment();
    }

    public void sseConnected() {
        activeSseConnections.incrementAndGet();
    }

    public void sseDisconnected() {
        activeSseConnections.updateAndGet(value -> Math.max(0, value - 1));
    }

    public void webSocketConnected() {
        activeWebSocketSessions.incrementAndGet();
    }

    public void webSocketDisconnected() {
        activeWebSocketSessions.updateAndGet(value -> Math.max(0, value - 1));
    }
}
