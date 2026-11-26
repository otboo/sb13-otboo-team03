package com.otboo.team03.notification.entity;

import com.otboo.team03.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NotificationTest {

    @Test
    void 알림을_생성하면_읽지_않은_상태로_생성된다() {
        User receiver = new User("receiver@otboo.io", "encoded-password", "수신자");
        User actor = new User("actor@otboo.io", "encoded-password", "행위자");
        UUID relatedId = UUID.randomUUID();
        UUID sourceEventId = UUID.randomUUID();

        Notification notification = new Notification(
                receiver,
                actor,
                "DIRECT_MESSAGE_RECEIVED",
                relatedId,
                "새 메시지",
                "새로운 메시지가 도착했습니다.",
                NotificationLevel.INFO,
                sourceEventId
        );

        assertAll(
                () -> assertEquals(receiver, notification.getReceiver()),
                () -> assertEquals(actor, notification.getActor()),
                () -> assertEquals("DIRECT_MESSAGE_RECEIVED", notification.getNotificationType()),
                () -> assertEquals(relatedId, notification.getRelatedId()),
                () -> assertEquals("새 메시지", notification.getTitle()),
                () -> assertEquals("새로운 메시지가 도착했습니다.", notification.getContent()),
                () -> assertEquals(NotificationLevel.INFO, notification.getLevel()),
                () -> assertEquals(sourceEventId, notification.getSourceEventId()),
                () -> assertNull(notification.getReadAt())
        );
    }

    @Test
    void 알림을_읽음_처리하면_읽은_시각이_저장된다() {
        Notification notification = new Notification(
                new User("receiver@otboo.io", "encoded-password", "수신자"),
                null,
                "ROLE_CHANGED",
                null,
                "권한 변경",
                "권한이 변경되었습니다.",
                NotificationLevel.WARNING,
                UUID.randomUUID()
        );
        Instant readAt = Instant.now();

        notification.markAsRead(readAt);

        assertEquals(readAt, notification.getReadAt());
    }

    @Test
    void 이미_읽은_알림은_읽음_시각을_덮어쓰지_않는다() {
        Notification notification = new Notification(
                new User("receiver@otboo.io", "encoded-password", "수신자"),
                null,
                "FOLLOWED",
                null,
                "팔로우 알림",
                "새로운 팔로워가 있습니다.",
                NotificationLevel.INFO,
                UUID.randomUUID()
        );
        Instant firstReadAt = Instant.now();
        Instant secondReadAt = firstReadAt.plusSeconds(10);

        notification.markAsRead(firstReadAt);
        notification.markAsRead(secondReadAt);

        assertEquals(firstReadAt, notification.getReadAt());
    }
}
