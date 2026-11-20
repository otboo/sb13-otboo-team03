package com.otboo.team03.notification.redis;

import com.otboo.team03.notification.dto.response.NotificationDto;
import com.otboo.team03.notification.entity.NotificationLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRedisPublisherTest {

    private static final String CHANNEL = "otboo:notifications";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    void 알림을_지정된_Redis_채널로_발행한다() throws Exception {
        NotificationRedisPublisher publisher = new NotificationRedisPublisher(
                redisTemplate,
                objectMapper,
                CHANNEL
        );
        NotificationDto notification = notification();
        when(objectMapper.writeValueAsString(new NotificationRedisMessage(
                notification.receiverId(), notification
        ))).thenReturn("redis-message");

        publisher.publish(notification);

        verify(redisTemplate).convertAndSend(CHANNEL, "redis-message");
    }

    @Test
    void 메시지_변환에_실패하면_예외를_던진다() throws Exception {
        NotificationRedisPublisher publisher = new NotificationRedisPublisher(
                redisTemplate,
                objectMapper,
                CHANNEL
        );
        NotificationDto notification = notification();
        when(objectMapper.writeValueAsString(new NotificationRedisMessage(
                notification.receiverId(), notification
        ))).thenThrow(new IllegalStateException("serialize failed"));

        assertThatThrownBy(() -> publisher.publish(notification))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis 알림 발행에 실패했습니다.");
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
