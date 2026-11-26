package com.otboo.team03.realtime.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.mockito.Mockito.*;

class JwtRevocationRedisPublisherTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final JwtRevocationRedisPublisher publisher = new JwtRevocationRedisPublisher(redisTemplate, "otboo:jwt-revocations");

    @Nested
    @DisplayName("JWT 무효화 이벤트 발행")
    class PublishTest {

        @Test
        @DisplayName("사용자 ID를 Redis Pub/Sub 채널에 발행한다")
        void publish_sendsUserIdToRedisChannel() {
            // given
            UUID userId = UUID.randomUUID();

            // when
            publisher.publish(userId);

            // then
            verify(redisTemplate).convertAndSend("otboo:jwt-revocations", userId.toString());
        }
    }
}
