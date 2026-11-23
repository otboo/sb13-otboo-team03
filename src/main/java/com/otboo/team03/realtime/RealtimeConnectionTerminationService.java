package com.otboo.team03.realtime;

import com.otboo.team03.notification.sse.SseConnectionManager;
import com.otboo.team03.realtime.websocket.WebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RealtimeConnectionTerminationService {

    private final SseConnectionManager sseConnectionManager;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    public void disconnectAll(UUID userId) {
        sseConnectionManager.disconnectAll(userId);
        webSocketSessionRegistry.disconnectAll(userId);
    }
}
