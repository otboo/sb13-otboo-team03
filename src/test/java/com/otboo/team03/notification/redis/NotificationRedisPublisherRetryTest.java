package com.otboo.team03.notification.redis;

import com.otboo.team03.notification.dto.response.NotificationDto;
import com.otboo.team03.notification.entity.NotificationLevel;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(NotificationRedisPublisherRetryTest.TestConfig.class)
class NotificationRedisPublisherRetryTest {

    @jakarta.annotation.Resource
    private NotificationRedisPublisher publisher;

    @jakarta.annotation.Resource
    private StringRedisTemplate redisTemplate;

    @Test
    void 일시적인_Redis_연결_실패는_재시도한다() {
        NotificationDto notification = new NotificationDto(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                "알림",
                "내용",
                NotificationLevel.INFO
        );
        when(redisTemplate.convertAndSend("otboo:notifications", "message"))
                .thenThrow(new RedisConnectionFailureException("Redis unavailable"))
                .thenReturn(1L);

        publisher.publish(notification);

        verify(redisTemplate, org.mockito.Mockito.times(2))
                .convertAndSend("otboo:notifications", "message");
    }

    @Configuration
    @EnableRetry
    static class TestConfig {

        @Bean
        StringRedisTemplate redisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            ObjectMapper objectMapper = mock(ObjectMapper.class);
            try {
                when(objectMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                        .thenReturn("message");
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            return objectMapper;
        }

        @Bean
        NotificationRedisPublisher notificationRedisPublisher(
                StringRedisTemplate redisTemplate,
                ObjectMapper objectMapper
        ) {
            return new NotificationRedisPublisher(
                    redisTemplate,
                    objectMapper,
                    "otboo:notifications"
            );
        }
    }
}
