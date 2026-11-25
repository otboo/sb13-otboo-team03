package com.otboo.team03.notification.kafka;

import com.otboo.team03.notification.event.NotificationEvent;
import com.otboo.team03.notification.processed.service.ProcessedEventService;
import com.otboo.team03.notification.redis.NotificationRedisPublisher;
import com.otboo.team03.notification.service.NotificationEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
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
                .ifPresent(notificationRedisPublisher::publish);
    }
}
