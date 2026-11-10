package com.otboo.team03.notification.service;

import com.otboo.team03.notification.entity.Notification;
import com.otboo.team03.notification.event.NotificationEvent;
import com.otboo.team03.notification.repository.NotificationRepository;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.exception.UserNotFoundException;
import com.otboo.team03.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationEventService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public void handle(NotificationEvent event) {
        String notificationType = event.eventType().name();

        if (notificationRepository.existsByReceiver_IdAndNotificationTypeAndSourceEventId(
                event.receiverId(),
                notificationType,
                event.eventId()
        )) {
            return;
        }

        User receiver = userRepository.findById(event.receiverId())
                .orElseThrow(UserNotFoundException::new);
        User actor = event.actorId() == null
                ? null
                : userRepository.findById(event.actorId())
                        .orElseThrow(UserNotFoundException::new);

        notificationRepository.save(new Notification(
                receiver,
                actor,
                notificationType,
                event.relatedId(),
                event.title(),
                event.content(),
                event.level(),
                event.eventId()
        ));
    }
}
