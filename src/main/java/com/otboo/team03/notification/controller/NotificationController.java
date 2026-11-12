package com.otboo.team03.notification.controller;

import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.global.security.AuthenticatedUserIdExtractor;
import com.otboo.team03.notification.dto.response.NotificationDto;
import com.otboo.team03.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<CursorResponse<NotificationDto>> getNotifications(
            @RequestParam int limit,
            @RequestParam(required = false) Instant cursor,
            @RequestParam(required = false) UUID idAfter,
            Authentication authentication
    ) {
        UUID receiverId = AuthenticatedUserIdExtractor.extract(authentication);

        return ResponseEntity.ok(notificationService.getNotifications(
                receiverId,
                cursor,
                idAfter,
                limit
        ));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> markAsRead(
            @PathVariable UUID notificationId,
            Authentication authentication
    ) {
        UUID receiverId = AuthenticatedUserIdExtractor.extract(authentication);
        notificationService.markAsRead(receiverId, notificationId, Instant.now());

        return ResponseEntity.noContent().build();
    }

}
