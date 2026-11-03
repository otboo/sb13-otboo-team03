package com.otboo.team03.notification.service;

import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.notification.dto.response.NotificationDto;
import com.otboo.team03.notification.entity.Notification;
import com.otboo.team03.notification.exception.NotificationNotFoundException;
import com.otboo.team03.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String SORT_BY_CREATED_AT = "createdAt";
    private static final String SORT_DIRECTION_DESCENDING = "DESCENDING";

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public CursorResponse<NotificationDto> getNotifications(
            UUID receiverId,
            Instant cursor,
            UUID idAfter,
            int limit
    ) {
        validateLimit(limit);

        PageRequest pageRequest = PageRequest.of(0, limit + 1);
        List<Notification> notifications = cursor == null
                ? notificationRepository.findByReceiver_IdOrderByCreatedAtDescIdDesc(
                        receiverId,
                        pageRequest
                )
                : notificationRepository.findPageByReceiverIdAfterCursor(
                        receiverId,
                        cursor,
                        idAfter,
                        pageRequest
                );

        boolean hasNext = notifications.size() > limit;
        List<Notification> page = hasNext
                ? notifications.subList(0, limit)
                : notifications;
        List<NotificationDto> data = page.stream()
                .map(NotificationDto::from)
                .toList();

        Notification lastNotification = page.isEmpty() ? null : page.get(page.size() - 1);

        return new CursorResponse<>(
                data,
                hasNext && lastNotification != null
                        ? lastNotification.getCreatedAt().toString()
                        : null,
                hasNext && lastNotification != null
                        ? lastNotification.getId()
                        : null,
                hasNext,
                notificationRepository.countByReceiver_Id(receiverId),
                SORT_BY_CREATED_AT,
                SORT_DIRECTION_DESCENDING
        );
    }

    @Transactional
    public boolean markAsRead(UUID receiverId, UUID notificationId, Instant readAt) {
        return notificationRepository.findByIdAndReceiver_Id(notificationId, receiverId)
                .map(notification -> {
                    notification.markAsRead(readAt);
                    return true;
                })
                .orElseThrow(NotificationNotFoundException::new);
    }

    private void validateLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit은 1 이상이어야 합니다.");
        }
    }
}
