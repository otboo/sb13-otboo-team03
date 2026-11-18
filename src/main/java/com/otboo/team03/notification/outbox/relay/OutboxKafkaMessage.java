package com.otboo.team03.notification.outbox.relay;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record OutboxKafkaMessage(
        UUID eventId,
        String eventType,
        short schemaVersion,
        Instant occurredAt,
        UUID aggregateId,
        JsonNode payload
) {
}
