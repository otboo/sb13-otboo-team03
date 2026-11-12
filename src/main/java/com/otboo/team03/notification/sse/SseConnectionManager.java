package com.otboo.team03.notification.sse;

import com.otboo.team03.notification.dto.response.NotificationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SseConnectionManager {

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;
    private static final String NOTIFICATION_EVENT_NAME = "notifications";

    private final ConcurrentHashMap<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter connect(UUID receiverId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        Runnable cleanup = () -> remove(receiverId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());

        register(receiverId, emitter);

        return emitter;
    }

    void register(UUID receiverId, SseEmitter emitter) {
        emitters.compute(receiverId, (id, connections) -> {
            Set<SseEmitter> updatedConnections = connections == null
                    ? ConcurrentHashMap.newKeySet()
                    : connections;
            updatedConnections.add(emitter);
            return updatedConnections;
        });
    }

    public void send(UUID receiverId, NotificationDto notification) {
        Set<SseEmitter> receiverEmitters = emitters.get(receiverId);
        if (receiverEmitters == null) {
            return;
        }

        for (SseEmitter emitter : receiverEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(notification.id().toString())
                        .name(NOTIFICATION_EVENT_NAME)
                        .data(notification));
            } catch (IOException exception) {
                handleSendFailure(receiverId, emitter, exception,
                        "SSE 알림 전송에 실패했습니다.");
            } catch (IllegalStateException exception) {
                handleSendFailure(receiverId, emitter, exception,
                        "SSE 연결이 종료된 상태입니다.");
            }
        }
    }

    @Scheduled(fixedDelayString = "${scheduler.sse-heartbeat.fixed-delay:PT15S}")
    public void sendHeartbeats() {
        for (var entry : emitters.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException exception) {
                    handleSendFailure(entry.getKey(), emitter, exception,
                            "SSE heartbeat 전송에 실패했습니다.");
                } catch (IllegalStateException exception) {
                    handleSendFailure(entry.getKey(), emitter, exception,
                            "SSE heartbeat 전송 중 연결이 종료되었습니다.");
                }
            }
        }
    }

    private void handleSendFailure(
            UUID receiverId,
            SseEmitter emitter,
            RuntimeException exception,
            String message
    ) {
        log.warn("{} receiverId={}", message, receiverId, exception);
        remove(receiverId, emitter);
        emitter.completeWithError(exception);
    }

    private void handleSendFailure(
            UUID receiverId,
            SseEmitter emitter,
            IOException exception,
            String message
    ) {
        log.warn("{} receiverId={}", message, receiverId, exception);
        remove(receiverId, emitter);
    }

    private void remove(UUID receiverId, SseEmitter emitter) {
        emitters.computeIfPresent(receiverId, (id, connections) -> {
            connections.remove(emitter);
            return connections.isEmpty() ? null : connections;
        });
    }

    void disconnect(UUID receiverId, SseEmitter emitter) {
        remove(receiverId, emitter);
    }

    int connectionCount(UUID receiverId) {
        Set<SseEmitter> receiverEmitters = emitters.get(receiverId);
        return receiverEmitters == null ? 0 : receiverEmitters.size();
    }
}
