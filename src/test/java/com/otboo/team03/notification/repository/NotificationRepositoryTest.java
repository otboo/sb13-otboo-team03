package com.otboo.team03.notification.repository;

import com.otboo.team03.notification.entity.Notification;
import com.otboo.team03.notification.entity.NotificationLevel;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
class NotificationRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 수신자별_알림을_커서로_조회한다() {
        User receiver = userRepository.save(
                new User("receiver@otboo.io", "encoded-password", "수신자")
        );

        notificationRepository.save(newNotification(receiver, "첫 번째"));
        notificationRepository.save(newNotification(receiver, "두 번째"));
        notificationRepository.save(newNotification(receiver, "세 번째"));

        List<Notification> firstPage = notificationRepository.findByReceiver_IdOrderByCreatedAtDescIdDesc(
                receiver.getId(),
                PageRequest.of(0, 2)
        );

        Notification cursorItem = firstPage.get(firstPage.size() - 1);
        List<Notification> nextPage = notificationRepository.findPageByReceiverIdAfterCursor(
                receiver.getId(),
                cursorItem.getCreatedAt(),
                cursorItem.getId(),
                PageRequest.of(0, 2)
        );

        assertThat(firstPage).hasSize(2);
        assertThat(nextPage).hasSize(1);
        assertThat(nextPage).doesNotContainAnyElementsOf(firstPage);
    }

    @Test
    void 다른_수신자의_알림은_조회하지_않는다() {
        User receiver = userRepository.save(
                new User("receiver@otboo.io", "encoded-password", "수신자")
        );
        User otherReceiver = userRepository.save(
                new User("other@otboo.io", "encoded-password", "다른 수신자")
        );
        notificationRepository.save(newNotification(receiver, "수신자 알림"));
        notificationRepository.save(newNotification(otherReceiver, "다른 알림"));

        List<Notification> notifications = notificationRepository.findByReceiver_IdOrderByCreatedAtDescIdDesc(
                receiver.getId(),
                PageRequest.of(0, 20)
        );

        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getReceiver()).isEqualTo(receiver);
    }

    private Notification newNotification(User receiver, String content) {
        return new Notification(
                receiver,
                null,
                "TEST",
                UUID.randomUUID(),
                "알림",
                content,
                NotificationLevel.INFO,
                UUID.randomUUID()
        );
    }
}
