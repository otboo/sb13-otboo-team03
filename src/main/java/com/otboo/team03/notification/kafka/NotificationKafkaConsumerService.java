package com.otboo.team03.notification.kafka;

import com.otboo.team03.notification.dto.response.NotificationDto;
import com.otboo.team03.notification.event.NotificationEvent;
import com.otboo.team03.notification.processed.service.ProcessedEventService;
import com.otboo.team03.notification.service.NotificationEventService;
import com.otboo.team03.notification.sse.SseConnectionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class NotificationKafkaConsumerService {

    static final String CONSUMER_NAME = "notification-consumer";

    private final ProcessedEventService processedEventService;
    private final NotificationEventService notificationEventService;
    private final SseConnectionManager sseConnectionManager;

    @Transactional
    public void process(NotificationEvent event) {
        if (!processedEventService.tryMarkProcessed(CONSUMER_NAME, event.eventId())) {
            return;
        }

        notificationEventService.handle(event)
                .ifPresent(notification -> sendToSseAfterCommit(event, notification));
    }

    private void sendToSseAfterCommit(NotificationEvent event, NotificationDto notification) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sseConnectionManager.send(event.receiverId(), notification);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sseConnectionManager.send(event.receiverId(), notification);
            }
        });
    }
}
