package com.otboo.team03.realtime;

import com.otboo.team03.notification.sse.SseConnectionManager;
import com.otboo.team03.realtime.websocket.WebSocketSessionRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RealtimeConnectionTerminationServiceTest {

    private final SseConnectionManager sseConnectionManager = mock(SseConnectionManager.class);
    private final WebSocketSessionRegistry webSocketSessionRegistry = mock(WebSocketSessionRegistry.class);
    private final RealtimeConnectionTerminationService service = new RealtimeConnectionTerminationService(
            sseConnectionManager,
            webSocketSessionRegistry
    );

    @Test
    void 사용자_실시간_연결을_로컬에서_종료한다() {
        UUID userId = UUID.randomUUID();

        service.disconnectAll(userId);

        verify(sseConnectionManager).disconnectAll(userId);
        verify(webSocketSessionRegistry).disconnectAll(userId);
    }
}
