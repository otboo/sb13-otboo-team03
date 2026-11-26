package com.otboo.team03.notification.kafka;

import com.otboo.team03.notification.dto.response.NotificationDto;
import com.otboo.team03.notification.event.NotificationEvent;
import com.otboo.team03.notification.processed.service.ProcessedEventService;
import com.otboo.team03.notification.redis.NotificationRedisPublisher;
import com.otboo.team03.notification.service.NotificationEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
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
    private NotificationRedisPublisher notificationRedisPublisher;

    @Test
    void 최초_이벤트는_알림을_저장하고_Redis로_발행한다() {
        NotificationKafkaConsumerService service = new NotificationKafkaConsumerService(
                processedEventService,
                notificationEventService,
                notificationRedisPublisher
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
        verify(notificationRedisPublisher).publish(notification);
    }

    @Test
    void 이미_처리한_이벤트는_알림을_저장하지_않고_Redis로_발행하지_않는다() {
        NotificationKafkaConsumerService service = new NotificationKafkaConsumerService(
                processedEventService,
                notificationEventService,
                notificationRedisPublisher
        );
        NotificationEvent event = event();
        when(processedEventService.tryMarkProcessed(
                NotificationKafkaConsumerService.CONSUMER_NAME,
                event.eventId()
        )).thenReturn(false);

        service.process(event);

        verify(notificationEventService, never()).handle(event);
        verifyNoInteractions(notificationRedisPublisher);
    }

    @Test
    void 알림이_생성되지_않으면_Redis로_발행하지_않는다() {
        NotificationKafkaConsumerService service = newService();
        NotificationEvent event = event();
        when(processedEventService.tryMarkProcessed(
                NotificationKafkaConsumerService.CONSUMER_NAME,
                event.eventId()
        )).thenReturn(true);
        when(notificationEventService.handle(event)).thenReturn(Optional.empty());

        service.process(event);

        verify(notificationEventService).handle(event);
        verifyNoInteractions(notificationRedisPublisher);
    }

    @Test
    void 알림_저장_트랜잭션이_커밋된_후_Redis로_발행한다() {
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

            verifyNoInteractions(notificationRedisPublisher);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(notificationRedisPublisher).publish(notification);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void 트랜잭션_커밋_후_Redis_발행에_실패해도_알림_저장_결과는_유지한다() {
        NotificationKafkaConsumerService service = newService();
        NotificationEvent event = event();
        NotificationDto notification = notification(event);
        when(processedEventService.tryMarkProcessed(
                NotificationKafkaConsumerService.CONSUMER_NAME,
                event.eventId()
        )).thenReturn(true);
        when(notificationEventService.handle(event)).thenReturn(Optional.of(notification));
        org.mockito.Mockito.doThrow(new IllegalStateException("Redis unavailable"))
                .when(notificationRedisPublisher).publish(notification);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.process(event);

            assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit))
                    .doesNotThrowAnyException();
            verify(notificationRedisPublisher).publish(notification);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private NotificationKafkaConsumerService newService() {
        return new NotificationKafkaConsumerService(
                processedEventService,
                notificationEventService,
                notificationRedisPublisher
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
