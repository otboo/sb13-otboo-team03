package com.otboo.team03.notification.controller;

import com.otboo.team03.global.security.AuthenticatedUserIdExtractor;
import com.otboo.team03.notification.dto.response.NotificationDto;
import com.otboo.team03.notification.service.NotificationService;
import com.otboo.team03.notification.sse.SseConnectionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sse")
public class SseController {

    private final SseConnectionManager sseConnectionManager;
    private final NotificationService notificationService;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(
            Authentication authentication,
            @RequestParam(value = "LastEventId", required = false) UUID lastEventId,
            @RequestHeader(value = "Last-Event-ID", required = false) UUID lastEventHeader
    ) {
        UUID receiverId = AuthenticatedUserIdExtractor.extract(authentication);
        UUID reconnectEventId = lastEventHeader != null ? lastEventHeader : lastEventId;

        if (reconnectEventId == null) {
            return sseConnectionManager.connect(receiverId);
        }

        List<NotificationDto> missedNotifications = notificationService.getNotificationsAfterEvent(
                receiverId,
                reconnectEventId
        );
        return sseConnectionManager.connect(receiverId, missedNotifications);
    }
}
