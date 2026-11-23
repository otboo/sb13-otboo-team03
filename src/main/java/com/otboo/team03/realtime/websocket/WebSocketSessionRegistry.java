package com.otboo.team03.realtime.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class WebSocketSessionRegistry {

    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> sessionUsers = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Set<String>> userSessions = new ConcurrentHashMap<>();

    public void register(String sessionId, WebSocketSession session) {
        if (sessionId != null && session != null) {
            sessions.put(sessionId, session);
        }
    }

    public void bindUser(String sessionId, UUID userId) {
        if (sessionId == null || userId == null || !sessions.containsKey(sessionId)) {
            return;
        }

        UUID previousUserId = sessionUsers.put(sessionId, userId);
        if (previousUserId != null && !previousUserId.equals(userId)) {
            removeFromUserSessions(previousUserId, sessionId);
        }

        userSessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
    }

    public void unregister(String sessionId) {
        if (sessionId == null) {
            return;
        }

        sessions.remove(sessionId);
        UUID userId = sessionUsers.remove(sessionId);
        if (userId != null) {
            removeFromUserSessions(userId, sessionId);
        }
    }

    public void disconnectAll(UUID userId) {
        Set<String> sessionIds = userSessions.remove(userId);
        if (sessionIds == null) {
            return;
        }

        for (String sessionId : sessionIds) {
            sessionUsers.remove(sessionId, userId);
            WebSocketSession session = sessions.remove(sessionId);
            if (session == null || !session.isOpen()) {
                continue;
            }

            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException exception) {
                log.warn("WebSocket 강제 종료에 실패했습니다. userId={}, sessionId={}",
                        userId, sessionId, exception);
            }
        }
    }

    int connectionCount(UUID userId) {
        Set<String> sessionIds = userSessions.get(userId);
        return sessionIds == null ? 0 : sessionIds.size();
    }

    private void removeFromUserSessions(UUID userId, String sessionId) {
        userSessions.computeIfPresent(userId, (ignored, sessionIds) -> {
            sessionIds.remove(sessionId);
            return sessionIds.isEmpty() ? null : sessionIds;
        });
    }
}
