package com.otboo.team03.realtime.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketSessionRegistryTest {

    private final WebSocketSessionRegistry registry = new WebSocketSessionRegistry();

    @Test
    void 사용자별_모든_WebSocket_세션을_종료한다() throws IOException {
        UUID userId = UUID.randomUUID();
        WebSocketSession firstSession = session("session-1");
        WebSocketSession secondSession = session("session-2");
        registry.register("session-1", firstSession);
        registry.register("session-2", secondSession);
        registry.bindUser("session-1", userId);
        registry.bindUser("session-2", userId);

        registry.disconnectAll(userId);

        verify(firstSession).close(CloseStatus.POLICY_VIOLATION);
        verify(secondSession).close(CloseStatus.POLICY_VIOLATION);
        assertThat(registry.connectionCount(userId)).isZero();
    }

    @Test
    void WebSocket_연결이_끊기면_사용자별_목록에서_제거한다() {
        UUID userId = UUID.randomUUID();
        WebSocketSession session = session("session-1");
        registry.register("session-1", session);
        registry.bindUser("session-1", userId);

        registry.unregister("session-1");

        assertThat(registry.connectionCount(userId)).isZero();
    }

    @Test
    void 한_사용자의_전체_종료가_다른_사용자의_WebSocket_연결을_종료하지_않는다() throws IOException {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        WebSocketSession firstSession = session("session-1");
        WebSocketSession secondSession = session("session-2");
        registry.register("session-1", firstSession);
        registry.register("session-2", secondSession);
        registry.bindUser("session-1", firstUserId);
        registry.bindUser("session-2", secondUserId);

        registry.disconnectAll(firstUserId);

        verify(firstSession).close(CloseStatus.POLICY_VIOLATION);
        verify(secondSession, org.mockito.Mockito.never()).close(CloseStatus.POLICY_VIOLATION);
        assertThat(registry.connectionCount(firstUserId)).isZero();
        assertThat(registry.connectionCount(secondUserId)).isOne();
    }

    private WebSocketSession session(String sessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
