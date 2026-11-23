package com.otboo.team03.notification.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationMetricsAspectTest {

    @Mock
    private NotificationMetrics metrics;

    private NotificationMetricsAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new NotificationMetricsAspect(metrics);
    }

    @Test
    void 알림이_생성되면_생성_메트릭을_기록한다() {
        aspect.recordNotificationCreated(Optional.of("notification"));

        verify(metrics).notificationCreated();
    }

    @Test
    void 알림이_생성되지_않으면_생성_메트릭을_기록하지_않는다() {
        aspect.recordNotificationCreated(Optional.empty());

        verifyNoInteractions(metrics);
    }

    @Test
    void Redis_발행_성공과_실패_메트릭을_기록한다() {
        aspect.recordNotificationDelivered();
        aspect.recordRealtimeDeliveryFailure();

        verify(metrics).notificationDelivered();
        verify(metrics).realtimeDeliveryFailed();
    }

    @Test
    void Kafka_Consumer_실패_메트릭을_기록한다() {
        aspect.recordKafkaConsumerFailure();

        verify(metrics).kafkaConsumerFailed();
    }
}
