package com.otboo.team03.realtime.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JwtRevocationRedisPublisher {

    private final StringRedisTemplate redisTemplate;
    private final String channel;

    public JwtRevocationRedisPublisher(
            StringRedisTemplate redisTemplate,
            @Value("${otboo.redis.jwt-revocation-channel:otboo:jwt-revocations}") String channel
    ) {
        this.redisTemplate = redisTemplate;
        this.channel = channel;
    }

    public void publish(UUID userId) {
        redisTemplate.convertAndSend(channel, userId.toString());
    }
}
