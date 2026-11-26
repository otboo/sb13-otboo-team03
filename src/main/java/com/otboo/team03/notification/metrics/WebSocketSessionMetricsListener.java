package com.otboo.team03.notification.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;

@Component
@RequiredArgsConstructor
public class WebSocketSessionMetricsListener {

    private final NotificationMetrics metrics;

    @EventListener
    public void onSessionConnected(SessionConnectedEvent event) {
        metrics.webSocketConnected();
    }

    @EventListener
    public void onSessionDisconnected(SessionDisconnectEvent event) {
        metrics.webSocketDisconnected();
    }
}
