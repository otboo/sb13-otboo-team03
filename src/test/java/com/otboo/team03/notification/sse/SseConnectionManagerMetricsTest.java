package com.otboo.team03.notification.sse;

import com.otboo.team03.notification.metrics.NotificationMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SseConnectionManagerMetricsTest {

    @Mock
    private NotificationMetrics metrics;

    private SseConnectionManager connectionManager;

    @BeforeEach
    void setUp() {
        connectionManager = new SseConnectionManager(metrics);
    }

    @Test
    void SSE_연결이_등록되면_활성_연결_메트릭을_증가시킨다() {
        connectionManager.connect(UUID.randomUUID());

        verify(metrics).sseConnected();
    }

    @Test
    void SSE_연결이_제거되면_활성_연결_메트릭을_감소시킨다() {
        UUID receiverId = UUID.randomUUID();
        SseEmitter emitter = connectionManager.connect(receiverId);
        clearInvocations(metrics);

        connectionManager.disconnect(receiverId, emitter);

        verify(metrics).sseDisconnected();
    }
}
