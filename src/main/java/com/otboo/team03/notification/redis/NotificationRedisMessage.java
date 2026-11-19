package com.otboo.team03.notification.redis;

import com.otboo.team03.notification.dto.response.NotificationDto;

import java.util.UUID;

public record NotificationRedisMessage(
        UUID receiverId,
        NotificationDto notification
) {
}
