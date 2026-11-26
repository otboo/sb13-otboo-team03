package com.otboo.team03.notification.service;

import com.otboo.team03.notification.dto.response.NotificationDto;

import java.util.List;

public record NotificationReplayResult(
        List<NotificationDto> notifications,
        boolean hasMore
) {
    public static NotificationReplayResult empty() {
        return new NotificationReplayResult(List.of(), false);
    }
}
