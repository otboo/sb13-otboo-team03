package com.otboo.team03.notification.kafka;

import com.otboo.team03.notification.dto.response.NotificationDto;
import com.otboo.team03.notification.event.NotificationEvent;
import com.otboo.team03.notification.processed.service.ProcessedEventService;
import com.otboo.team03.notification.redis.NotificationRedisPublisher;
import com.otboo.team03.notification.service.NotificationEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationKafkaConsumerService {

    static final String CONSUMER_NAME = "notification-consumer";

    private final ProcessedEventService processedEventService;
    private final NotificationEventService notificationEventService;
    private final NotificationRedisPublisher notificationRedisPublisher;

    @Transactional
    public void process(NotificationEvent event) {
        if (!processedEventService.tryMarkProcessed(CONSUMER_NAME, event.eventId())) {
            return;
        }

        notificationEventService.handle(event)
                .ifPresent(this::publishAfterCommit);
    }

    private void publishAfterCommit(NotificationDto notification) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishToRedis(notification);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishToRedis(notification);
            }
        });
    }

    private void publishToRedis(NotificationDto notification) {
        try {
            notificationRedisPublisher.publish(notification);
        } catch (RuntimeException exception) {
            log.error("Redis 알림 발행에 실패했습니다. notificationId={}", notification.id(), exception);
        }
    }
}
