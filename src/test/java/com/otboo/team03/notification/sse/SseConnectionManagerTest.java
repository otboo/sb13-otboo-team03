package com.otboo.team03.notification.sse;

import com.otboo.team03.notification.dto.response.NotificationDto;
import com.otboo.team03.notification.entity.NotificationLevel;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SseConnectionManagerTest {

    private final SseConnectionManager connectionManager = new SseConnectionManager();

    @Test
    void 사용자별_SSE_연결을_등록한다() {
        UUID receiverId = UUID.randomUUID();

        SseEmitter emitter = connectionManager.connect(receiverId);

        assertThat(emitter).isNotNull();
        assertThat(connectionManager.connectionCount(receiverId)).isEqualTo(1);
    }

    @Test
    void 사용자에게_연결된_SSE로_알림을_전송한다() {
        UUID receiverId = UUID.randomUUID();
        connectionManager.connect(receiverId);
        NotificationDto notification = notification(receiverId);

        connectionManager.send(receiverId, notification);

        assertThat(connectionManager.connectionCount(receiverId)).isEqualTo(1);
    }

    @Test
    void 한_연결의_IllegalStateException이_다른_연결_전송을_중단시키지_않는다() throws Exception {
        UUID receiverId = UUID.randomUUID();
        SseEmitter failedEmitter = mock(SseEmitter.class);
        SseEmitter activeEmitter = mock(SseEmitter.class);
        doThrow(new IllegalStateException("이미 종료된 연결입니다."))
                .when(failedEmitter)
                .send(any(SseEmitter.SseEventBuilder.class));
        connectionManager.register(receiverId, failedEmitter);
        connectionManager.register(receiverId, activeEmitter);

        connectionManager.send(receiverId, notification(receiverId));

        verify(activeEmitter).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(connectionManager.connectionCount(receiverId)).isEqualTo(1);
    }

    @Test
    void 연결이_없는_사용자에게는_전송하지_않는다() {
        connectionManager.send(UUID.randomUUID(), notification(UUID.randomUUID()));

        assertThat(connectionManager.connectionCount(UUID.randomUUID())).isZero();
    }

    @Test
    void SSE_연결이_완료되면_연결을_제거한다() {
        UUID receiverId = UUID.randomUUID();
        SseEmitter emitter = connectionManager.connect(receiverId);

        connectionManager.disconnect(receiverId, emitter);

        assertThat(connectionManager.connectionCount(receiverId)).isZero();
    }

    private NotificationDto notification(UUID receiverId) {
        return new NotificationDto(
                UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"),
                receiverId,
                "알림",
                "알림 내용",
                NotificationLevel.INFO
        );
    }
}
