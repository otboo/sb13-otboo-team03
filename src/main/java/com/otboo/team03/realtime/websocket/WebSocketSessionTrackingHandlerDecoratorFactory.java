package com.otboo.team03.realtime.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class WebSocketSessionTrackingHandlerDecoratorFactory
        implements WebSocketHandlerDecoratorFactory {

    private final WebSocketSessionRegistry sessionRegistry;

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessionRegistry.register(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus)
                    throws Exception {
                sessionRegistry.unregister(Objects.requireNonNull(session).getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        };
    }
}
