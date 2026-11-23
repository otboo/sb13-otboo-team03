package com.otboo.team03.notification.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMetricsTest {

    @Test
    void 알림_실시간_전달_Kafka_실패_메트릭을_기록한다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NotificationMetrics metrics = new NotificationMetrics(registry);

        metrics.notificationCreated();
        metrics.notificationDelivered();
        metrics.realtimeDeliveryFailed();
        metrics.kafkaConsumerFailed();

        assertThat(registry.get("notifications_created_total").counter().count()).isEqualTo(1);
        assertThat(registry.get("notifications_delivered_total").counter().count()).isEqualTo(1);
        assertThat(registry.get("realtime_delivery_failure_total").counter().count()).isEqualTo(1);
        assertThat(registry.get("kafka_consumer_failure_total").counter().count()).isEqualTo(1);
    }

    @Test
    void SSE와_WebSocket_활성_세션_수를_증감한다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NotificationMetrics metrics = new NotificationMetrics(registry);

        metrics.sseConnected();
        metrics.webSocketConnected();
        assertThat(registry.get("active_sse_connections").gauge().value()).isEqualTo(1);
        assertThat(registry.get("active_websocket_sessions").gauge().value()).isEqualTo(1);

        metrics.sseDisconnected();
        metrics.sseDisconnected();
        metrics.webSocketDisconnected();
        metrics.webSocketDisconnected();
        assertThat(registry.get("active_sse_connections").gauge().value()).isZero();
        assertThat(registry.get("active_websocket_sessions").gauge().value()).isZero();
    }
}
