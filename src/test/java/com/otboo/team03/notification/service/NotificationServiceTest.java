package com.otboo.team03.notification.service;

import com.otboo.team03.notification.entity.Notification;
import com.otboo.team03.notification.entity.NotificationLevel;
import com.otboo.team03.notification.exception.NotificationNotFoundException;
import com.otboo.team03.notification.repository.NotificationRepository;
import com.otboo.team03.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void 알림_목록을_조회한다() {
        UUID receiverId = UUID.randomUUID();
        User receiver = new User("receiver@otboo.io", "encoded-password", "수신자");
        Notification notification = new Notification(
                receiver,
                null,
                "TEST",
                UUID.randomUUID(),
                "알림 제목",
                "알림 내용",
                NotificationLevel.INFO,
                UUID.randomUUID()
        );

        when(notificationRepository.findByReceiver_IdOrderByCreatedAtDescIdDesc(eq(receiverId), any(Pageable.class)))
                .thenReturn(List.of(notification));
        when(notificationRepository.countByReceiver_Id(receiverId)).thenReturn(1L);

        var response = notificationService.getNotifications(receiverId, null, null, 20);

        assertThat(response.data()).hasSize(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.totalCount()).isEqualTo(1L);
        assertThat(response.sortBy()).isEqualTo("createdAt");
        assertThat(response.sortDirection()).isEqualTo("DESCENDING");
    }

    @Test
    void 커서_이후_알림_목록을_조회한다() {
        UUID receiverId = UUID.randomUUID();
        Instant cursor = Instant.now();
        UUID idAfter = UUID.randomUUID();
        Notification notification = new Notification(
                new User("receiver@otboo.io", "encoded-password", "수신자"),
                null,
                "TEST",
                UUID.randomUUID(),
                "알림 제목",
                "알림 내용",
                NotificationLevel.INFO,
                UUID.randomUUID()
        );

        when(notificationRepository.findPageByReceiverIdAfterCursor(
                eq(receiverId), eq(cursor), eq(idAfter), any(Pageable.class)
        )).thenReturn(List.of(notification));
        when(notificationRepository.countByReceiver_Id(receiverId)).thenReturn(1L);

        var response = notificationService.getNotifications(receiverId, cursor, idAfter, 20);

        assertThat(response.data()).hasSize(1);
        assertThat(response.hasNext()).isFalse();
        verify(notificationRepository).findPageByReceiverIdAfterCursor(
                eq(receiverId), eq(cursor), eq(idAfter), any(Pageable.class)
        );
    }

    @Test
    void 알림이_없으면_빈_목록을_반환한다() {
        UUID receiverId = UUID.randomUUID();

        when(notificationRepository.findByReceiver_IdOrderByCreatedAtDescIdDesc(
                eq(receiverId), any(Pageable.class)
        )).thenReturn(List.of());
        when(notificationRepository.countByReceiver_Id(receiverId)).thenReturn(0L);

        var response = notificationService.getNotifications(receiverId, null, null, 20);

        assertThat(response.data()).isEmpty();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.nextIdAfter()).isNull();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.totalCount()).isZero();
    }

    @Test
    void 조회_결과가_limit보다_많으면_다음_커서를_생성한다() {
        UUID receiverId = UUID.randomUUID();
        UUID lastNotificationId = UUID.randomUUID();
        Instant lastCreatedAt = Instant.now();
        User receiver = mock(User.class);
        Notification firstNotification = mock(Notification.class);
        Notification additionalNotification = mock(Notification.class);

        when(receiver.getId()).thenReturn(receiverId);
        when(firstNotification.getReceiver()).thenReturn(receiver);
        when(firstNotification.getTitle()).thenReturn("첫 알림");
        when(firstNotification.getContent()).thenReturn("첫 알림 내용");
        when(firstNotification.getLevel()).thenReturn(NotificationLevel.INFO);
        when(firstNotification.getCreatedAt()).thenReturn(lastCreatedAt);
        when(firstNotification.getId()).thenReturn(lastNotificationId);

        when(notificationRepository.findByReceiver_IdOrderByCreatedAtDescIdDesc(
                eq(receiverId), any(Pageable.class)
        )).thenReturn(List.of(firstNotification, additionalNotification));
        when(notificationRepository.countByReceiver_Id(receiverId)).thenReturn(2L);

        var response = notificationService.getNotifications(receiverId, null, null, 1);

        assertThat(response.data()).hasSize(1);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(lastCreatedAt.toString());
        assertThat(response.nextIdAfter()).isEqualTo(lastNotificationId);
    }

    @Test
    void 유효하지_않은_limit은_거부한다() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> notificationService.getNotifications(
                        UUID.randomUUID(), null, null, 0
                ));
    }

    @Test
    void 수신자_본인의_알림을_읽음_처리한다() {
        UUID receiverId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Notification notification = new Notification(
                new User("receiver@otboo.io", "encoded-password", "수신자"),
                null,
                "TEST",
                null,
                "알림 제목",
                "알림 내용",
                NotificationLevel.INFO,
                UUID.randomUUID()
        );
        Instant readAt = Instant.now();

        when(notificationRepository.findByIdAndReceiver_Id(notificationId, receiverId))
                .thenReturn(Optional.of(notification));

        assertThat(notificationService.markAsRead(receiverId, notificationId, readAt)).isTrue();
        assertThat(notification.getReadAt()).isEqualTo(readAt);
    }

    @Test
    void 다른_사용자의_알림은_알림_없음_예외가_발생한다() {
        UUID receiverId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        when(notificationRepository.findByIdAndReceiver_Id(notificationId, receiverId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(
                receiverId,
                notificationId,
                Instant.now()
        )).isInstanceOf(NotificationNotFoundException.class);
        verify(notificationRepository).findByIdAndReceiver_Id(notificationId, receiverId);
    }
}
