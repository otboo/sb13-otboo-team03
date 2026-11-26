package com.otboo.team03.notification.sse;

import com.otboo.team03.notification.dto.response.NotificationDto;
import com.otboo.team03.notification.metrics.NotificationMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SseConnectionManager {

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;
    private static final String NOTIFICATION_EVENT_NAME = "notifications";
    private static final String NOTIFICATION_SYNC_REQUIRED_EVENT_NAME = "notifications-sync-required";
    private static final String NOTIFICATION_SYNC_REQUIRED_REASON = "REPLAY_LIMIT_EXCEEDED";

    private final ConcurrentHashMap<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SseEmitter, ReplayState> replayStates = new ConcurrentHashMap<>();
    private final NotificationMetrics metrics;

    public SseConnectionManager() {
        this.metrics = null;
    }

    @Autowired
    public SseConnectionManager(NotificationMetrics metrics) {
        this.metrics = metrics;
    }

    public SseEmitter connect(UUID receiverId) {
        SseEmitter emitter = createEmitter(receiverId);
        register(receiverId, emitter);
        return emitter;
    }

    public SseEmitter connect(UUID receiverId, List<NotificationDto> missedNotifications) {
        SseEmitter emitter = connect(receiverId);
        sendMissedNotifications(receiverId, emitter, missedNotifications);
        return emitter;
    }

    public SseEmitter connectForReplay(UUID receiverId) {
        SseEmitter emitter = createEmitter(receiverId);
        registerForReplay(receiverId, emitter);
        return emitter;
    }

    void registerForReplay(UUID receiverId, SseEmitter emitter) {
        replayStates.put(emitter, new ReplayState());
        register(receiverId, emitter);
    }

    public void completeReplay(
            UUID receiverId,
            SseEmitter emitter,
            List<NotificationDto> missedNotifications,
            boolean hasMore
    ) {
        ReplayState replayState = replayStates.get(emitter);
        if (replayState == null) {
            sendMissedNotifications(receiverId, emitter, missedNotifications);
            return;
        }

        synchronized (replayState) {
            Map<UUID, NotificationDto> notificationsById = new LinkedHashMap<>();
            missedNotifications.forEach(notification ->
                    notificationsById.putIfAbsent(notification.id(), notification)
            );
            replayState.bufferedNotifications.forEach(notificationsById::putIfAbsent);

            for (NotificationDto notification : notificationsById.values()) {
                if (!send(receiverId, emitter, notification)) {
                    replayStates.remove(emitter, replayState);
                    return;
                }
            }

            if (hasMore && sendSyncRequired(receiverId, emitter)) {
                log.info("SSE 복구 상한을 초과해 전체 동기화가 필요합니다. receiverId={}", receiverId);
            }
            replayStates.remove(emitter, replayState);
        }
    }

    void sendMissedNotifications(
            UUID receiverId,
            SseEmitter emitter,
            List<NotificationDto> missedNotifications
    ) {
        for (NotificationDto notification : missedNotifications) {
            if (!send(receiverId, emitter, notification)) {
                break;
            }
        }
    }

    void register(UUID receiverId, SseEmitter emitter) {
        emitters.compute(receiverId, (id, connections) -> {
            Set<SseEmitter> updatedConnections = connections == null
                    ? ConcurrentHashMap.newKeySet()
                    : connections;
            if (updatedConnections.add(emitter) && metrics != null) {
                metrics.sseConnected();
            }
            return updatedConnections;
        });
    }

    public void send(UUID receiverId, NotificationDto notification) {
        Set<SseEmitter> receiverEmitters = emitters.get(receiverId);
        if (receiverEmitters == null) {
            return;
        }

        for (SseEmitter emitter : receiverEmitters) {
            ReplayState replayState = replayStates.get(emitter);
            if (replayState == null) {
                send(receiverId, emitter, notification);
                continue;
            }

            synchronized (replayState) {
                if (replayStates.containsKey(emitter)) {
                    replayState.bufferedNotifications.putIfAbsent(notification.id(), notification);
                } else {
                    send(receiverId, emitter, notification);
                }
            }
        }
    }

    private SseEmitter createEmitter(UUID receiverId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        Runnable cleanup = () -> remove(receiverId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());
        return emitter;
    }

    private boolean send(UUID receiverId, SseEmitter emitter, NotificationDto notification) {
        try {
            emitter.send(SseEmitter.event()
                    .id(notification.id().toString())
                    .name(NOTIFICATION_EVENT_NAME)
                    .data(notification));
            return true;
        } catch (IOException exception) {
            handleSendFailure(receiverId, emitter, exception,
                    "SSE 알림 전송에 실패했습니다.");
        } catch (IllegalStateException exception) {
            handleSendFailure(receiverId, emitter, exception,
                    "SSE 연결이 종료된 상태입니다.");
        }
        return false;
    }

    private boolean sendSyncRequired(UUID receiverId, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event()
                    .name(NOTIFICATION_SYNC_REQUIRED_EVENT_NAME)
                    .data(Map.of("reason", NOTIFICATION_SYNC_REQUIRED_REASON)));
            return true;
        } catch (IOException exception) {
            log.warn("SSE 전체 동기화 필요 신호 전송에 실패했습니다.", exception);
            remove(receiverId, emitter);
            return false;
        } catch (IllegalStateException exception) {
            log.warn("SSE 전체 동기화 필요 신호 전송 중 연결이 종료되었습니다.", exception);
            remove(receiverId, emitter);
            emitter.completeWithError(exception);
            return false;
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
        replayStates.remove(emitter);
        emitters.computeIfPresent(receiverId, (id, connections) -> {
            if (connections.remove(emitter) && metrics != null) {
                metrics.sseDisconnected();
            }
            return connections.isEmpty() ? null : connections;
        });
    }

    public void disconnect(UUID receiverId, SseEmitter emitter) {
        remove(receiverId, emitter);
        emitter.complete();
    }

    public void disconnectAll(UUID receiverId) {
        Set<SseEmitter> receiverEmitters = emitters.remove(receiverId);
        if (receiverEmitters == null) {
            return;
        }

        if (metrics != null) {
            receiverEmitters.forEach(ignored -> metrics.sseDisconnected());
        }
        receiverEmitters.forEach(SseEmitter::complete);
    }

    int connectionCount(UUID receiverId) {
        Set<SseEmitter> receiverEmitters = emitters.get(receiverId);
        return receiverEmitters == null ? 0 : receiverEmitters.size();
    }

    private static final class ReplayState {
        private final Map<UUID, NotificationDto> bufferedNotifications = new LinkedHashMap<>();
    }
}
