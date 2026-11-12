package com.otboo.team03.notification.sse;

import com.otboo.team03.notification.dto.response.NotificationDto;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
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

        emitters.compute(receiverId, (id, connections) -> {
            Set<SseEmitter> updatedConnections = connections == null
                    ? ConcurrentHashMap.newKeySet()
                    : connections;
            updatedConnections.add(emitter);
            return updatedConnections;
        });

        return emitter;
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
                remove(receiverId, emitter);
                emitter.completeWithError(exception);
            }
        }
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
