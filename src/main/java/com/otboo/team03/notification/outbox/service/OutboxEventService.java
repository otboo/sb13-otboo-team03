package com.otboo.team03.notification.outbox.service;

import com.otboo.team03.notification.event.NotificationEvent;
import com.otboo.team03.notification.outbox.entity.OutboxEvent;
import com.otboo.team03.notification.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OutboxEventService implements OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void publish(NotificationEvent event, String aggregateType, String partitionKey) {
        Objects.requireNonNull(event, "event must not be null");
        validateText(aggregateType, "aggregateType");
        validateText(partitionKey, "partitionKey");

        Instant normalizedOccurredAt = event.occurredAt().truncatedTo(ChronoUnit.MICROS);
        NotificationEvent normalizedEvent = new NotificationEvent(
                event.eventId(),
                event.eventType(),
                event.schemaVersion(),
                normalizedOccurredAt,
                event.aggregateId(),
                event.receiverId(),
                event.actorId(),
                event.relatedId(),
                event.title(),
                event.content(),
                event.level()
        );

        outboxEventRepository.save(new OutboxEvent(
                normalizedEvent.eventId(),
                normalizedEvent.eventType().name(),
                toSchemaVersion(normalizedEvent.schemaVersion()),
                aggregateType,
                normalizedEvent.aggregateId(),
                partitionKey,
                toPayload(normalizedEvent),
                normalizedOccurredAt,
                Instant.now()
        ));
    }

    private JsonNode toPayload(NotificationEvent event) {
        try {
            return objectMapper.readTree(objectMapper.writeValueAsString(event));
        } catch (Exception exception) {
            throw new IllegalStateException("알림 이벤트 payload 변환에 실패했습니다.", exception);
        }
    }

    private short toSchemaVersion(int schemaVersion) {
        if (schemaVersion < 0 || schemaVersion > Short.MAX_VALUE) {
            throw new IllegalArgumentException("schemaVersion은 0 이상 32767 이하여야 합니다.");
        }
        return (short) schemaVersion;
    }

    private void validateText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은 필수입니다.");
        }
    }
}
