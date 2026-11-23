package com.otboo.team03.notification.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class WebSocketSessionMetricsListenerTest {

    @Mock
    private NotificationMetrics metrics;

    @Mock
    private SessionConnectedEvent connectedEvent;

    @Mock
    private SessionDisconnectEvent disconnectedEvent;

    private WebSocketSessionMetricsListener listener;

    @BeforeEach
    void setUp() {
        listener = new WebSocketSessionMetricsListener(metrics);
    }

    @Test
    void WebSocket_연결_이벤트가_발생하면_활성_세션을_증가시킨다() {
        listener.onSessionConnected(connectedEvent);

        verify(metrics).webSocketConnected();
    }

    @Test
    void WebSocket_종료_이벤트가_발생하면_활성_세션을_감소시킨다() {
        listener.onSessionDisconnected(disconnectedEvent);

        verify(metrics).webSocketDisconnected();
    }

    @Test
    void WebSocket_이벤트가_발생하면_실제_활성_세션_게이지가_변경된다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NotificationMetrics actualMetrics = new NotificationMetrics(registry);
        WebSocketSessionMetricsListener actualListener =
                new WebSocketSessionMetricsListener(actualMetrics);

        actualListener.onSessionConnected(mock(SessionConnectedEvent.class));
        assertThat(registry.get("active_websocket_sessions").gauge().value())
                .isEqualTo(1);

        actualListener.onSessionDisconnected(mock(SessionDisconnectEvent.class));
        assertThat(registry.get("active_websocket_sessions").gauge().value())
                .isZero();
    }
}
