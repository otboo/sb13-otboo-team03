package com.otboo.team03.notification.outbox.controller;

import com.otboo.team03.notification.outbox.relay.OutboxEventRelayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/outbox-events")
public class OutboxEventAdminController {

    private final OutboxEventRelayService relayService;

    @PostMapping("/{eventId}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> retry(@PathVariable UUID eventId) {
        relayService.retryFailedEvent(eventId);
        return ResponseEntity.noContent().build();
    }
}
