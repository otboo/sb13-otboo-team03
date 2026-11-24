package com.otboo.team03.notification.sse;

import com.otboo.team03.notification.dto.response.NotificationDto;
import com.otboo.team03.notification.entity.NotificationLevel;
import com.otboo.team03.notification.metrics.NotificationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

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

    @Test
    void 실제_SSE_연결_수가_연결과_해제에_따라_변경된다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SseConnectionManager actualManager = new SseConnectionManager(
                new NotificationMetrics(registry)
        );
        UUID receiverId = UUID.randomUUID();

        SseEmitter emitter = actualManager.connect(receiverId);

        assertThat(registry.get("active_sse_connections").gauge().value())
                .isEqualTo(1);

        actualManager.disconnect(receiverId, emitter);

        assertThat(registry.get("active_sse_connections").gauge().value())
                .isZero();
    }

    @Test
    void 동일한_SSE_Emitter를_중복_등록해도_연결_메트릭은_한번만_증가한다() {
        UUID receiverId = UUID.randomUUID();
        SseEmitter emitter = org.mockito.Mockito.mock(SseEmitter.class);

        connectionManager.register(receiverId, emitter);
        connectionManager.register(receiverId, emitter);

        verify(metrics, times(1)).sseConnected();
    }

    @Test
    void SSE_전송_실패로_연결이_제거되면_해제_메트릭을_기록한다() throws IOException {
        UUID receiverId = UUID.randomUUID();
        SseEmitter emitter = org.mockito.Mockito.mock(SseEmitter.class);
        doThrow(new IOException("전송 실패"))
                .when(emitter)
                .send(any(SseEmitter.SseEventBuilder.class));
        connectionManager.register(receiverId, emitter);
        clearInvocations(metrics);

        connectionManager.send(receiverId, new NotificationDto(
                UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"),
                receiverId,
                "알림",
                "알림 내용",
                NotificationLevel.INFO
        ));

        verify(metrics).sseDisconnected();
    }
}
