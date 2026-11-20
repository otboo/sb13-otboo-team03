package com.otboo.team03.notification.kafka;

import com.otboo.team03.notification.event.NotificationEvent;
import com.otboo.team03.notification.outbox.relay.OutboxKafkaMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class NotificationKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationKafkaConsumerService consumerService;

    @KafkaListener(
            id = "notificationKafkaConsumer",
            topics = "${otboo.kafka.notification-topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String message) {
        try {
            OutboxKafkaMessage envelope = objectMapper.readValue(message, OutboxKafkaMessage.class);
            validateEnvelope(envelope);

            NotificationEvent event = objectMapper.treeToValue(
                    envelope.payload(),
                    NotificationEvent.class
            );
            validateEvent(envelope, event);
            consumerService.process(event);
        } catch (Exception exception) {
            throw new IllegalStateException("Kafka 알림 이벤트 처리에 실패했습니다.", exception);
        }
    }

    private void validateEnvelope(OutboxKafkaMessage envelope) {
        if (envelope == null
                || envelope.eventId() == null
                || envelope.eventType() == null
                || envelope.occurredAt() == null
                || envelope.aggregateId() == null
                || envelope.payload() == null) {
            throw new IllegalArgumentException("Kafka 이벤트 envelope의 필수 값이 누락되었습니다.");
        }
    }

    private void validateEvent(OutboxKafkaMessage envelope, NotificationEvent event) {
        if (event == null
                || event.eventId() == null
                || event.eventType() == null
                || event.occurredAt() == null
                || event.receiverId() == null
                || event.title() == null
                || event.content() == null
                || event.level() == null) {
            throw new IllegalArgumentException("Kafka 알림 이벤트의 필수 값이 누락되었습니다.");
        }

        if (!envelope.eventId().equals(event.eventId())
                || !envelope.eventType().equals(event.eventType().name())
                || envelope.schemaVersion() != event.schemaVersion()
                || !envelope.occurredAt().equals(event.occurredAt())) {
            throw new IllegalArgumentException("Kafka 이벤트 envelope과 payload 정보가 일치하지 않습니다.");
        }

        if (event.schemaVersion() != NotificationEvent.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("지원하지 않는 알림 이벤트 schemaVersion입니다.");
        }
    }
}
