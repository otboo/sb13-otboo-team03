package com.otboo.team03.notification.sse;

import com.otboo.team03.notification.dto.response.NotificationDto;
import com.otboo.team03.notification.entity.NotificationLevel;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
    void 사용자에게_연결된_SSE로_알림을_전송한다() throws Exception {
        UUID receiverId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        connectionManager.register(receiverId, emitter);
        NotificationDto notification = notification(receiverId);

        connectionManager.send(receiverId, notification);

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(connectionManager.connectionCount(receiverId)).isEqualTo(1);
    }

    @Test
    void 누락된_알림과_함께_SSE_연결을_등록한다() {
        UUID receiverId = UUID.randomUUID();

        SseEmitter emitter = connectionManager.connect(receiverId, List.of(notification(receiverId)));

        assertThat(emitter).isNotNull();
        assertThat(connectionManager.connectionCount(receiverId)).isEqualTo(1);
    }

    @Test
    void 연결된_모든_SSE에_heartbeat를_전송한다() throws Exception {
        UUID receiverId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        connectionManager.register(receiverId, emitter);

        connectionManager.sendHeartbeats();

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
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
        SseEmitter emitter = mock(SseEmitter.class);
        connectionManager.register(receiverId, emitter);

        connectionManager.disconnect(receiverId, emitter);

        assertThat(connectionManager.connectionCount(receiverId)).isZero();
        verify(emitter).complete();
    }

    @Test
    void 연결_추가와_삭제가_동시에_발생해도_연결_목록의_정합성을_유지한다() throws Exception {
        UUID receiverId = UUID.randomUUID();
        int operationCount = 20;
        List<SseEmitter> emittersToRemove = new ArrayList<>();
        for (int index = 0; index < operationCount; index++) {
            SseEmitter emitter = mock(SseEmitter.class);
            emittersToRemove.add(emitter);
            connectionManager.register(receiverId, emitter);
        }

        int taskCount = operationCount * 2;
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (SseEmitter emitter : emittersToRemove) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    connectionManager.disconnect(receiverId, emitter);
                    return null;
                }));
            }
            for (int index = 0; index < operationCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    connectionManager.connect(receiverId);
                    return null;
                }));
            }

            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }

            assertThat(connectionManager.connectionCount(receiverId)).isEqualTo(operationCount);
        } finally {
            executor.shutdownNow();
        }
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
