package com.otboo.team03.notification.kafka;

import com.otboo.team03.notification.dto.response.NotificationDto;
import com.otboo.team03.notification.event.NotificationEvent;
import com.otboo.team03.notification.processed.service.ProcessedEventService;
import com.otboo.team03.notification.service.NotificationEventService;
import com.otboo.team03.notification.sse.SseConnectionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaConsumerServiceTest {

    @Mock
    private ProcessedEventService processedEventService;

    @Mock
    private NotificationEventService notificationEventService;

    @Mock
    private SseConnectionManager sseConnectionManager;

    @Test
    void 최초_이벤트는_알림을_저장하고_SSE로_전달한다() {
        NotificationKafkaConsumerService service = new NotificationKafkaConsumerService(
                processedEventService,
                notificationEventService,
                sseConnectionManager
        );
        NotificationEvent event = event();
        NotificationDto notification = new NotificationDto(
                UUID.randomUUID(),
                Instant.now(),
                event.receiverId(),
                "새 메시지",
                "내용",
                com.otboo.team03.notification.entity.NotificationLevel.INFO
        );
        when(processedEventService.tryMarkProcessed(
                NotificationKafkaConsumerService.CONSUMER_NAME,
                event.eventId()
        )).thenReturn(true);
        when(notificationEventService.handle(event)).thenReturn(Optional.of(notification));

        service.process(event);

        verify(notificationEventService).handle(event);
        verify(sseConnectionManager).send(event.receiverId(), notification);
    }

    @Test
    void 이미_처리한_이벤트는_알림을_저장하지_않고_SSE로_전달하지_않는다() {
        NotificationKafkaConsumerService service = new NotificationKafkaConsumerService(
                processedEventService,
                notificationEventService,
                sseConnectionManager
        );
        NotificationEvent event = event();
        when(processedEventService.tryMarkProcessed(
                NotificationKafkaConsumerService.CONSUMER_NAME,
                event.eventId()
        )).thenReturn(false);

        service.process(event);

        verify(notificationEventService, never()).handle(event);
        verifyNoInteractions(sseConnectionManager);
    }

    @Test
    void 알림이_생성되지_않으면_SSE로_전달하지_않는다() {
        NotificationKafkaConsumerService service = newService();
        NotificationEvent event = event();
        when(processedEventService.tryMarkProcessed(
                NotificationKafkaConsumerService.CONSUMER_NAME,
                event.eventId()
        )).thenReturn(true);
        when(notificationEventService.handle(event)).thenReturn(Optional.empty());

        service.process(event);

        verify(notificationEventService).handle(event);
        verifyNoInteractions(sseConnectionManager);
    }

    @Test
    void 트랜잭션이_커밋된_후_SSE로_전달한다() {
        NotificationKafkaConsumerService service = newService();
        NotificationEvent event = event();
        NotificationDto notification = notification(event);
        when(processedEventService.tryMarkProcessed(
                NotificationKafkaConsumerService.CONSUMER_NAME,
                event.eventId()
        )).thenReturn(true);
        when(notificationEventService.handle(event)).thenReturn(Optional.of(notification));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.process(event);

            verifyNoInteractions(sseConnectionManager);
            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            synchronizations.forEach(TransactionSynchronization::afterCommit);

            verify(sseConnectionManager).send(event.receiverId(), notification);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private NotificationKafkaConsumerService newService() {
        return new NotificationKafkaConsumerService(
                processedEventService,
                notificationEventService,
                sseConnectionManager
        );
    }

    private NotificationDto notification(NotificationEvent event) {
        return new NotificationDto(
                UUID.randomUUID(),
                Instant.now(),
                event.receiverId(),
                "새 메시지",
                "내용",
                com.otboo.team03.notification.entity.NotificationLevel.INFO
        );
    }

    private NotificationEvent event() {
        return new NotificationEvent(
                UUID.randomUUID(),
                com.otboo.team03.notification.event.NotificationEventType.DIRECT_MESSAGE_RECEIVED,
                NotificationEvent.CURRENT_SCHEMA_VERSION,
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "새 메시지",
                "내용",
                com.otboo.team03.notification.entity.NotificationLevel.INFO
        );
    }
}
