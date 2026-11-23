package com.otboo.team03.notification.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import static org.mockito.Mockito.verify;

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
}
