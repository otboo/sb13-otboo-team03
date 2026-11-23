package com.otboo.team03.notification.metrics;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Aspect
@Component
@RequiredArgsConstructor
public class NotificationMetricsAspect {

    private final NotificationMetrics metrics;

    @AfterReturning(
            pointcut = "execution(* com.otboo.team03.notification.service.NotificationEventService.handle(..))",
            returning = "result"
    )
    void recordNotificationCreated(Object result) {
        if (result instanceof Optional<?> optional && optional.isPresent()) {
            metrics.notificationCreated();
        }
    }

    @AfterReturning(
            "execution(* com.otboo.team03.notification.redis.NotificationRedisPublisher.publish(..))"
    )
    void recordNotificationDelivered() {
        metrics.notificationDelivered();
    }

    @AfterThrowing(
            pointcut = "execution(* com.otboo.team03.notification.redis.NotificationRedisPublisher.publish(..))"
    )
    void recordRealtimeDeliveryFailure() {
        metrics.realtimeDeliveryFailed();
    }

    @AfterThrowing(
            pointcut = "execution(* com.otboo.team03.notification.kafka.NotificationKafkaConsumer.consume(..))"
    )
    void recordKafkaConsumerFailure() {
        metrics.kafkaConsumerFailed();
    }
}
