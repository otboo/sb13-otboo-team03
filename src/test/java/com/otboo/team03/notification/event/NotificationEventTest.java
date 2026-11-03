package com.otboo.team03.notification.event;

import com.otboo.team03.notification.entity.NotificationLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEventTest {

    @Test
    void 알림_이벤트는_공통_발행_정보를_가진다() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID relatedId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        NotificationEvent event = new NotificationEvent(
                eventId,
                NotificationEventType.FEED_LIKED,
                NotificationEvent.CURRENT_SCHEMA_VERSION,
                occurredAt,
                aggregateId,
                receiverId,
                actorId,
                relatedId,
                "새로운 좋아요",
                "내 피드에 좋아요가 추가되었습니다.",
                NotificationLevel.INFO
        );

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.eventType()).isEqualTo(NotificationEventType.FEED_LIKED);
        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.occurredAt()).isEqualTo(occurredAt);
        assertThat(event.aggregateId()).isEqualTo(aggregateId);
        assertThat(event.receiverId()).isEqualTo(receiverId);
        assertThat(event.actorId()).isEqualTo(actorId);
        assertThat(event.relatedId()).isEqualTo(relatedId);
        assertThat(event.level()).isEqualTo(NotificationLevel.INFO);
    }

    @Test
    void 알림_트리거_이벤트_종류를_공통으로_정의한다() {
        assertThat(NotificationEventType.values())
                .containsExactly(
                        NotificationEventType.USER_ROLE_CHANGED,
                        NotificationEventType.CLOTHES_ATTRIBUTE_CREATED,
                        NotificationEventType.FEED_CREATED,
                        NotificationEventType.FEED_LIKED,
                        NotificationEventType.FEED_COMMENTED,
                        NotificationEventType.FOLLOWING_USER_FEED_CREATED,
                        NotificationEventType.USER_FOLLOWED,
                        NotificationEventType.DIRECT_MESSAGE_RECEIVED
                );
    }
}
