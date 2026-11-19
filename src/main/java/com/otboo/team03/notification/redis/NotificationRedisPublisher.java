package com.otboo.team03.notification.redis;

import com.otboo.team03.notification.dto.response.NotificationDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class NotificationRedisPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String notificationChannel;

    public NotificationRedisPublisher(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${otboo.redis.notification-channel:otboo:notifications}") String notificationChannel
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.notificationChannel = notificationChannel;
    }

    public void publish(NotificationDto notification) {
        try {
            String message = objectMapper.writeValueAsString(
                    new NotificationRedisMessage(notification.receiverId(), notification)
            );
            redisTemplate.convertAndSend(notificationChannel, message);
        } catch (Exception exception) {
            throw new IllegalStateException("Redis 알림 발행에 실패했습니다.", exception);
        }
    }
}
