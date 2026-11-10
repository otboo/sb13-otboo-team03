package com.otboo.team03.notification.service;

import com.otboo.team03.notification.entity.Notification;
import com.otboo.team03.notification.entity.NotificationLevel;
import com.otboo.team03.notification.event.NotificationEvent;
import com.otboo.team03.notification.event.NotificationEventType;
import com.otboo.team03.notification.repository.NotificationRepository;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationEventService notificationEventService;

    @Test
    void 알림_이벤트를_알림으로_저장한다() {
        UUID eventId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID relatedId = UUID.randomUUID();
        User receiver = new User("receiver@otboo.io", "password", "수신자");
        User actor = new User("actor@otboo.io", "password", "행위자");
        NotificationEvent event = event(
                eventId,
                receiverId,
                actorId,
                relatedId
        );

        when(notificationRepository.existsByReceiver_IdAndNotificationTypeAndSourceEventId(
                receiverId, NotificationEventType.FEED_LIKED.name(), eventId
        )).thenReturn(false);
        when(userRepository.findById(receiverId)).thenReturn(java.util.Optional.of(receiver));
        when(userRepository.findById(actorId)).thenReturn(java.util.Optional.of(actor));

        notificationEventService.handle(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification notification = captor.getValue();
        assertThat(notification.getReceiver()).isSameAs(receiver);
        assertThat(notification.getActor()).isSameAs(actor);
        assertThat(notification.getNotificationType()).isEqualTo("FEED_LIKED");
        assertThat(notification.getRelatedId()).isEqualTo(relatedId);
        assertThat(notification.getSourceEventId()).isEqualTo(eventId);
    }

    @Test
    void 이미_처리한_이벤트는_중복_저장하지_않는다() {
        UUID eventId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        NotificationEvent event = event(
                NotificationEventType.USER_ROLE_CHANGED,
                eventId,
                receiverId,
                null,
                null
        );
        when(notificationRepository.existsByReceiver_IdAndNotificationTypeAndSourceEventId(
                receiverId, NotificationEventType.USER_ROLE_CHANGED.name(), eventId
        )).thenReturn(true);

        notificationEventService.handle(event);

        verify(notificationRepository, never()).save(any(Notification.class));
        verify(userRepository, never()).findById(any());
    }

    @Test
    void 행위자가_없는_알림도_저장한다() {
        UUID eventId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        User receiver = new User("receiver@otboo.io", "password", "수신자");
        NotificationEvent event = event(
                NotificationEventType.USER_ROLE_CHANGED,
                eventId,
                receiverId,
                null,
                null
        );
        when(notificationRepository.existsByReceiver_IdAndNotificationTypeAndSourceEventId(
                receiverId, NotificationEventType.USER_ROLE_CHANGED.name(), eventId
        )).thenReturn(false);
        when(userRepository.findById(receiverId)).thenReturn(java.util.Optional.of(receiver));

        notificationEventService.handle(event);

        verify(notificationRepository).save(any(Notification.class));
    }

    private NotificationEvent event(UUID eventId, UUID receiverId, UUID actorId, UUID relatedId) {
        return event(NotificationEventType.FEED_LIKED, eventId, receiverId, actorId, relatedId);
    }

    private NotificationEvent event(
            NotificationEventType eventType,
            UUID eventId,
            UUID receiverId,
            UUID actorId,
            UUID relatedId
    ) {
        return new NotificationEvent(
                eventId,
                eventType,
                NotificationEvent.CURRENT_SCHEMA_VERSION,
                Instant.now(),
                relatedId,
                receiverId,
                actorId,
                relatedId,
                "피드에 좋아요가 등록되었습니다.",
                "사용자가 피드에 좋아요를 등록했습니다.",
                NotificationLevel.INFO
        );
    }
}
