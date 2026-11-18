package com.otboo.team03.notification.kafka;

import com.otboo.team03.notification.entity.NotificationLevel;
import com.otboo.team03.notification.event.NotificationEvent;
import com.otboo.team03.notification.event.NotificationEventType;
import com.otboo.team03.notification.outbox.relay.OutboxKafkaMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaConsumerTest {

    private static final String MESSAGE = "kafka-message";

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private NotificationKafkaConsumerService consumerService;

    private NotificationKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationKafkaConsumer(objectMapper, consumerService);
    }

    @Test
    void 유효한_Kafka_메시지를_알림_이벤트로_변환해_처리한다() throws Exception {
        NotificationEvent event = event();
        JsonNode payload = mock(JsonNode.class);
        OutboxKafkaMessage envelope = new OutboxKafkaMessage(
                event.eventId(),
                event.eventType().name(),
                (short) event.schemaVersion(),
                event.occurredAt(),
                event.aggregateId(),
                payload
        );
        when(objectMapper.readValue(MESSAGE, OutboxKafkaMessage.class)).thenReturn(envelope);
        when(objectMapper.treeToValue(payload, NotificationEvent.class)).thenReturn(event);

        consumer.consume(MESSAGE);

        verify(consumerService).process(event);
    }

    @Test
    void envelope과_payload의_eventId가_다르면_처리하지_않는다() throws Exception {
        NotificationEvent event = event();
        JsonNode payload = mock(JsonNode.class);
        OutboxKafkaMessage envelope = new OutboxKafkaMessage(
                UUID.randomUUID(),
                event.eventType().name(),
                (short) event.schemaVersion(),
                event.occurredAt(),
                event.aggregateId(),
                payload
        );
        when(objectMapper.readValue(MESSAGE, OutboxKafkaMessage.class)).thenReturn(envelope);
        when(objectMapper.treeToValue(payload, NotificationEvent.class)).thenReturn(event);

        assertThatThrownBy(() -> consumer.consume(MESSAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka 알림 이벤트 처리에 실패했습니다.");

        verifyNoInteractions(consumerService);
    }

    @Test
    void 지원하지_않는_schemaVersion이면_처리하지_않는다() throws Exception {
        NotificationEvent event = event(2);
        JsonNode payload = mock(JsonNode.class);
        OutboxKafkaMessage envelope = new OutboxKafkaMessage(
                event.eventId(),
                event.eventType().name(),
                (short) event.schemaVersion(),
                event.occurredAt(),
                event.aggregateId(),
                payload
        );
        when(objectMapper.readValue(MESSAGE, OutboxKafkaMessage.class)).thenReturn(envelope);
        when(objectMapper.treeToValue(payload, NotificationEvent.class)).thenReturn(event);

        assertThatThrownBy(() -> consumer.consume(MESSAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka 알림 이벤트 처리에 실패했습니다.");

        verifyNoInteractions(consumerService);
    }

    private NotificationEvent event() {
        return event(NotificationEvent.CURRENT_SCHEMA_VERSION);
    }

    private NotificationEvent event(int schemaVersion) {
        return new NotificationEvent(
                UUID.randomUUID(),
                NotificationEventType.DIRECT_MESSAGE_RECEIVED,
                schemaVersion,
                Instant.parse("2026-11-18T00:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "새 메시지",
                "새로운 메시지가 도착했습니다.",
                NotificationLevel.INFO
        );
    }
}
