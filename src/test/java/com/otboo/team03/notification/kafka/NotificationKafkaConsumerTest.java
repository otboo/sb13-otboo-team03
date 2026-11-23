package com.otboo.team03.notification.kafka;

import com.otboo.team03.notification.entity.NotificationLevel;
import com.otboo.team03.notification.event.NotificationEvent;
import com.otboo.team03.notification.event.NotificationEventType;
import com.otboo.team03.notification.outbox.relay.OutboxKafkaMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

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
    void WEATHER_ALERT는_actorId가_null이어도_처리한다() throws Exception {
        NotificationEvent event = new NotificationEvent(
                UUID.randomUUID(),
                NotificationEventType.WEATHER_ALERT,
                NotificationEvent.CURRENT_SCHEMA_VERSION,
                Instant.parse("2026-11-23T00:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                "날씨 알림",
                "비가 예상됩니다.",
                NotificationLevel.INFO
        );
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

    @Test
    void 잘못된_JSON이면_처리하지_않는다() throws Exception {
        when(objectMapper.readValue(MESSAGE, OutboxKafkaMessage.class))
                .thenThrow(new IllegalArgumentException("invalid json"));

        assertThatThrownBy(() -> consumer.consume(MESSAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka 알림 이벤트 처리에 실패했습니다.");

        verifyNoInteractions(consumerService);
    }

    @Test
    void envelope_필수값이_누락되면_처리하지_않는다() throws Exception {
        when(objectMapper.readValue(MESSAGE, OutboxKafkaMessage.class))
                .thenReturn(new OutboxKafkaMessage(
                        null,
                        "DIRECT_MESSAGE_RECEIVED",
                        (short) 1,
                        Instant.now(),
                        UUID.randomUUID(),
                        mock(JsonNode.class)
                ));

        assertThatThrownBy(() -> consumer.consume(MESSAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka 알림 이벤트 처리에 실패했습니다.");

        verifyNoInteractions(consumerService);
    }

    @Test
    void envelope가_null이면_처리하지_않는다() throws Exception {
        when(objectMapper.readValue(MESSAGE, OutboxKafkaMessage.class)).thenReturn(null);

        assertThatThrownBy(() -> consumer.consume(MESSAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka 알림 이벤트 처리에 실패했습니다.");

        verifyNoInteractions(consumerService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidEnvelopeProvider")
    void envelope의_각_필수값이_누락되면_처리하지_않는다(
            String description,
            OutboxKafkaMessage invalidEnvelope
    ) throws Exception {
        when(objectMapper.readValue(MESSAGE, OutboxKafkaMessage.class))
                .thenReturn(invalidEnvelope);

        assertThatThrownBy(() -> consumer.consume(MESSAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka 알림 이벤트 처리에 실패했습니다.");

        verifyNoInteractions(consumerService);
    }

    @Test
    void payload가_없으면_처리하지_않는다() throws Exception {
        when(objectMapper.readValue(MESSAGE, OutboxKafkaMessage.class))
                .thenReturn(new OutboxKafkaMessage(
                        UUID.randomUUID(),
                        "DIRECT_MESSAGE_RECEIVED",
                        (short) 1,
                        Instant.now(),
                        UUID.randomUUID(),
                        null
                ));

        assertThatThrownBy(() -> consumer.consume(MESSAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka 알림 이벤트 처리에 실패했습니다.");

        verifyNoInteractions(consumerService);
    }

    @Test
    void 알림_이벤트_필수값이_누락되면_처리하지_않는다() throws Exception {
        NotificationEvent invalidEvent = new NotificationEvent(
                UUID.randomUUID(),
                NotificationEventType.DIRECT_MESSAGE_RECEIVED,
                NotificationEvent.CURRENT_SCHEMA_VERSION,
                Instant.now(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "새 메시지",
                "내용",
                NotificationLevel.INFO
        );
        JsonNode payload = mock(JsonNode.class);
        when(objectMapper.readValue(MESSAGE, OutboxKafkaMessage.class))
                .thenReturn(envelopeFor(invalidEvent, payload));
        when(objectMapper.treeToValue(payload, NotificationEvent.class)).thenReturn(invalidEvent);

        assertThatThrownBy(() -> consumer.consume(MESSAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka 알림 이벤트 처리에 실패했습니다.");

        verifyNoInteractions(consumerService);
    }

    @Test
    void 변환된_알림_이벤트가_null이면_처리하지_않는다() throws Exception {
        JsonNode payload = mock(JsonNode.class);
        OutboxKafkaMessage envelope = validEnvelope(payload);
        when(objectMapper.readValue(MESSAGE, OutboxKafkaMessage.class)).thenReturn(envelope);
        when(objectMapper.treeToValue(payload, NotificationEvent.class)).thenReturn(null);

        assertThatThrownBy(() -> consumer.consume(MESSAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka 알림 이벤트 처리에 실패했습니다.");

        verifyNoInteractions(consumerService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidEventProvider")
    void 알림_이벤트의_각_필수값이_누락되면_처리하지_않는다(
            String description,
            NotificationEvent invalidEvent
    ) throws Exception {
        JsonNode payload = mock(JsonNode.class);
        when(objectMapper.readValue(MESSAGE, OutboxKafkaMessage.class))
                .thenReturn(validEnvelope(payload));
        when(objectMapper.treeToValue(payload, NotificationEvent.class)).thenReturn(invalidEvent);

        assertThatThrownBy(() -> consumer.consume(MESSAGE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka 알림 이벤트 처리에 실패했습니다.");

        verifyNoInteractions(consumerService);
    }

    @Test
    void envelope과_payload의_eventType이_다르면_처리하지_않는다() throws Exception {
        NotificationEvent event = event();
        JsonNode payload = mock(JsonNode.class);
        OutboxKafkaMessage envelope = new OutboxKafkaMessage(
                event.eventId(),
                NotificationEventType.USER_ROLE_CHANGED.name(),
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
    void envelope과_payload의_occurredAt이_다르면_처리하지_않는다() throws Exception {
        NotificationEvent event = event();
        JsonNode payload = mock(JsonNode.class);
        OutboxKafkaMessage envelope = new OutboxKafkaMessage(
                event.eventId(),
                event.eventType().name(),
                (short) event.schemaVersion(),
                event.occurredAt().plusSeconds(1),
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
    void envelope과_payload의_schemaVersion이_다르면_처리하지_않는다() throws Exception {
        NotificationEvent event = event();
        JsonNode payload = mock(JsonNode.class);
        OutboxKafkaMessage envelope = new OutboxKafkaMessage(
                event.eventId(),
                event.eventType().name(),
                (short) (event.schemaVersion() + 1),
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

    private static Stream<Arguments> invalidEnvelopeProvider() {
        UUID eventId = UUID.randomUUID();
        String eventType = NotificationEventType.DIRECT_MESSAGE_RECEIVED.name();
        Instant occurredAt = Instant.parse("2026-11-18T00:00:00Z");
        UUID aggregateId = UUID.randomUUID();
        JsonNode payload = mock(JsonNode.class);

        return Stream.of(
                Arguments.of("eventId 누락", new OutboxKafkaMessage(
                        null, eventType, (short) 1, occurredAt, aggregateId, payload
                )),
                Arguments.of("eventType 누락", new OutboxKafkaMessage(
                        eventId, null, (short) 1, occurredAt, aggregateId, payload
                )),
                Arguments.of("occurredAt 누락", new OutboxKafkaMessage(
                        eventId, eventType, (short) 1, null, aggregateId, payload
                )),
                Arguments.of("aggregateId 누락", new OutboxKafkaMessage(
                        eventId, eventType, (short) 1, occurredAt, null, payload
                )),
                Arguments.of("payload 누락", new OutboxKafkaMessage(
                        eventId, eventType, (short) 1, occurredAt, aggregateId, null
                ))
        );
    }

    private static Stream<Arguments> invalidEventProvider() {
        NotificationEvent validEvent = new NotificationKafkaConsumerTest().event();

        return Stream.of(
                Arguments.of("eventId 누락", new NotificationEvent(
                        null, validEvent.eventType(), validEvent.schemaVersion(), validEvent.occurredAt(),
                        validEvent.aggregateId(), validEvent.receiverId(), validEvent.actorId(), validEvent.relatedId(),
                        validEvent.title(), validEvent.content(), validEvent.level()
                )),
                Arguments.of("eventType 누락", new NotificationEvent(
                        validEvent.eventId(), null, validEvent.schemaVersion(), validEvent.occurredAt(),
                        validEvent.aggregateId(), validEvent.receiverId(), validEvent.actorId(), validEvent.relatedId(),
                        validEvent.title(), validEvent.content(), validEvent.level()
                )),
                Arguments.of("occurredAt 누락", new NotificationEvent(
                        validEvent.eventId(), validEvent.eventType(), validEvent.schemaVersion(), null,
                        validEvent.aggregateId(), validEvent.receiverId(), validEvent.actorId(), validEvent.relatedId(),
                        validEvent.title(), validEvent.content(), validEvent.level()
                )),
                Arguments.of("receiverId 누락", new NotificationEvent(
                        validEvent.eventId(), validEvent.eventType(), validEvent.schemaVersion(), validEvent.occurredAt(),
                        validEvent.aggregateId(), null, validEvent.actorId(), validEvent.relatedId(), validEvent.title(),
                        validEvent.content(), validEvent.level()
                )),
                Arguments.of("title 누락", new NotificationEvent(
                        validEvent.eventId(), validEvent.eventType(), validEvent.schemaVersion(), validEvent.occurredAt(),
                        validEvent.aggregateId(), validEvent.receiverId(), validEvent.actorId(), validEvent.relatedId(), null,
                        validEvent.content(), validEvent.level()
                )),
                Arguments.of("content 누락", new NotificationEvent(
                        validEvent.eventId(), validEvent.eventType(), validEvent.schemaVersion(), validEvent.occurredAt(),
                        validEvent.aggregateId(), validEvent.receiverId(), validEvent.actorId(), validEvent.relatedId(),
                        validEvent.title(), null, validEvent.level()
                )),
                Arguments.of("level 누락", new NotificationEvent(
                        validEvent.eventId(), validEvent.eventType(), validEvent.schemaVersion(), validEvent.occurredAt(),
                        validEvent.aggregateId(), validEvent.receiverId(), validEvent.actorId(), validEvent.relatedId(),
                        validEvent.title(), validEvent.content(), null
                ))
        );
    }

    private OutboxKafkaMessage validEnvelope(JsonNode payload) {
        NotificationEvent event = event();
        return envelopeFor(event, payload);
    }

    private OutboxKafkaMessage envelopeFor(NotificationEvent event, JsonNode payload) {
        return new OutboxKafkaMessage(
                event.eventId(),
                event.eventType().name(),
                (short) event.schemaVersion(),
                event.occurredAt(),
                event.aggregateId(),
                payload
        );
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
