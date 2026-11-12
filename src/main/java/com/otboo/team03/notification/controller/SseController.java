package com.otboo.team03.notification.controller;

import com.otboo.team03.global.security.AuthenticatedUserIdExtractor;
import com.otboo.team03.notification.sse.SseConnectionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.core.Authentication;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sse")
public class SseController {

    private final SseConnectionManager sseConnectionManager;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(Authentication authentication) {
        return sseConnectionManager.connect(AuthenticatedUserIdExtractor.extract(authentication));
    }
}
