package com.otboo.team03.notification.redis;

import com.otboo.team03.notification.dto.response.NotificationDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
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

    @Retryable(
            retryFor = {RedisConnectionFailureException.class, RedisSystemException.class},
            maxAttemptsExpression = "${otboo.redis.notification-publish.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${otboo.redis.notification-publish.backoff-ms:200}")
    )
    public void publish(NotificationDto notification) {
        String message;
        try {
            message = objectMapper.writeValueAsString(
                    new NotificationRedisMessage(notification.receiverId(), notification)
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Redis 알림 발행에 실패했습니다.", exception);
        }

        redisTemplate.convertAndSend(notificationChannel, message);
    }
}
