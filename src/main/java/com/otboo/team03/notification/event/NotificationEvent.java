package com.otboo.team03.notification.event;

import com.otboo.team03.notification.entity.NotificationLevel;

import java.time.Instant;
import java.util.UUID;

public record NotificationEvent(
        UUID eventId,
        NotificationEventType eventType,
        int schemaVersion,
        Instant occurredAt,
        UUID aggregateId,
        UUID receiverId,
        UUID actorId,
        UUID relatedId,
        String title,
        String content,
        NotificationLevel level
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}
