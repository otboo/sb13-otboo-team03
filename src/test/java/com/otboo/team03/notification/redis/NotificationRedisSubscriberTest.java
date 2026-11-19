package com.otboo.team03.notification.redis;

import com.otboo.team03.notification.dto.response.NotificationDto;
import com.otboo.team03.notification.entity.NotificationLevel;
import com.otboo.team03.notification.sse.SseConnectionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRedisSubscriberTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SseConnectionManager sseConnectionManager;

    @Mock
    private Message message;

    @Test
    void 유효한_Redis_알림을_로컬_SSE로_전달한다() throws Exception {
        NotificationRedisSubscriber subscriber = new NotificationRedisSubscriber(
                objectMapper,
                sseConnectionManager
        );
        NotificationDto notification = notification();
        NotificationRedisMessage redisMessage = new NotificationRedisMessage(
                notification.receiverId(), notification
        );
        when(message.getBody()).thenReturn("redis-message".getBytes());
        when(objectMapper.readValue("redis-message", NotificationRedisMessage.class))
                .thenReturn(redisMessage);

        subscriber.onMessage(message, null);

        verify(sseConnectionManager).send(notification.receiverId(), notification);
    }

    @Test
    void 잘못된_Redis_메시지는_SSE로_전달하지_않는다() throws Exception {
        NotificationRedisSubscriber subscriber = new NotificationRedisSubscriber(
                objectMapper,
                sseConnectionManager
        );
        when(message.getBody()).thenReturn("invalid-message".getBytes());
        when(objectMapper.readValue("invalid-message", NotificationRedisMessage.class))
                .thenThrow(new IllegalArgumentException("invalid message"));

        subscriber.onMessage(message, null);

        verifyNoInteractions(sseConnectionManager);
    }

    @Test
    void 수신자와_알림의_receiverId가_다르면_전달하지_않는다() throws Exception {
        NotificationRedisSubscriber subscriber = new NotificationRedisSubscriber(
                objectMapper,
                sseConnectionManager
        );
        NotificationDto notification = notification();
        NotificationRedisMessage redisMessage = new NotificationRedisMessage(
                UUID.randomUUID(), notification
        );
        when(message.getBody()).thenReturn("redis-message".getBytes());
        when(objectMapper.readValue("redis-message", NotificationRedisMessage.class))
                .thenReturn(redisMessage);

        subscriber.onMessage(message, null);

        verifyNoInteractions(sseConnectionManager);
    }

    @Test
    void Redis_알림_메시지가_null이면_전달하지_않는다() throws Exception {
        NotificationRedisSubscriber subscriber = new NotificationRedisSubscriber(
                objectMapper,
                sseConnectionManager
        );
        when(message.getBody()).thenReturn("redis-message".getBytes());
        when(objectMapper.readValue("redis-message", NotificationRedisMessage.class))
                .thenReturn(null);

        subscriber.onMessage(message, null);

        verifyNoInteractions(sseConnectionManager);
    }

    @Test
    void 수신자_ID가_null이면_전달하지_않는다() throws Exception {
        NotificationRedisSubscriber subscriber = new NotificationRedisSubscriber(
                objectMapper,
                sseConnectionManager
        );
        NotificationDto notification = notification();
        NotificationRedisMessage redisMessage = new NotificationRedisMessage(
                null,
                notification
        );
        when(message.getBody()).thenReturn("redis-message".getBytes());
        when(objectMapper.readValue("redis-message", NotificationRedisMessage.class))
                .thenReturn(redisMessage);

        subscriber.onMessage(message, null);

        verifyNoInteractions(sseConnectionManager);
    }

    @Test
    void 알림_본문이_null이면_전달하지_않는다() throws Exception {
        NotificationRedisSubscriber subscriber = new NotificationRedisSubscriber(
                objectMapper,
                sseConnectionManager
        );
        NotificationRedisMessage redisMessage = new NotificationRedisMessage(
                UUID.randomUUID(),
                null
        );
        when(message.getBody()).thenReturn("redis-message".getBytes());
        when(objectMapper.readValue("redis-message", NotificationRedisMessage.class))
                .thenReturn(redisMessage);

        subscriber.onMessage(message, null);

        verifyNoInteractions(sseConnectionManager);
    }

    private NotificationDto notification() {
        return new NotificationDto(
                UUID.randomUUID(),
                Instant.parse("2026-11-18T00:00:00Z"),
                UUID.randomUUID(),
                "알림",
                "알림 내용",
                NotificationLevel.INFO
        );
    }
}
