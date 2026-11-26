package com.otboo.team03.notification.controller;

import com.otboo.team03.global.security.AuthenticatedUserIdExtractor;
import com.otboo.team03.notification.service.NotificationService;
import com.otboo.team03.notification.service.NotificationReplayResult;
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
            @RequestParam(value = "lastEventId", required = false) UUID lastEventId,
            @RequestParam(value = "LastEventId", required = false) UUID legacyLastEventId,
            @RequestHeader(value = "Last-Event-ID", required = false) UUID lastEventHeader
    ) {
        UUID receiverId = AuthenticatedUserIdExtractor.extract(authentication);
        UUID reconnectEventId = firstNonNull(lastEventHeader, lastEventId, legacyLastEventId);

        if (reconnectEventId == null) {
            return sseConnectionManager.connect(receiverId);
        }

        SseEmitter emitter = sseConnectionManager.connectForReplay(receiverId);
        try {
            NotificationReplayResult replay = notificationService.getNotificationsAfterEvent(
                    receiverId,
                    reconnectEventId
            );
            sseConnectionManager.completeReplay(
                    receiverId,
                    emitter,
                    replay.notifications(),
                    replay.hasMore()
            );
            return emitter;
        } catch (RuntimeException exception) {
            sseConnectionManager.disconnect(receiverId, emitter);
            throw exception;
        }
    }

    private UUID firstNonNull(UUID... eventIds) {
        for (UUID eventId : eventIds) {
            if (eventId != null) {
                return eventId;
            }
        }
        return null;
    }
}
