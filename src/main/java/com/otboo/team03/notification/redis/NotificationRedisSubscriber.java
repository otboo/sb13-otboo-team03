package com.otboo.team03.notification.redis;

import com.otboo.team03.notification.sse.SseConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SseConnectionManager sseConnectionManager;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            NotificationRedisMessage redisMessage = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    NotificationRedisMessage.class
            );
            validate(redisMessage);
            sseConnectionManager.send(
                    redisMessage.receiverId(),
                    redisMessage.notification()
            );
        } catch (Exception exception) {
            log.warn("Redis 알림 메시지 처리에 실패했습니다.", exception);
        }
    }

    private void validate(NotificationRedisMessage redisMessage) {
        if (redisMessage == null
                || redisMessage.receiverId() == null
                || redisMessage.notification() == null
                || !redisMessage.receiverId().equals(redisMessage.notification().receiverId())) {
            throw new IllegalArgumentException("Redis 알림 메시지의 필수 값이 누락되었거나 일치하지 않습니다.");
        }
    }
}
