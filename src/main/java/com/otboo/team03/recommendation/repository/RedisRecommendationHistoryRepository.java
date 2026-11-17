package com.otboo.team03.recommendation.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class RedisRecommendationHistoryRepository {

    private static final String KEY_PREFIX = "recommendation:previous:";
    private static final Duration EXPIRATION = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;

    public Set<UUID> find(UUID userId, UUID weatherId) {
        String value = redisTemplate.opsForValue().get(key(userId, weatherId));
        if (value == null || value.isEmpty()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(UUID::fromString)
                .collect(Collectors.toUnmodifiableSet());
    }

    public void save(UUID userId, UUID weatherId, Set<UUID> clothesIds) {
        String value = clothesIds.stream()
                .map(UUID::toString)
                .sorted()
                .collect(Collectors.joining(","));
        redisTemplate.opsForValue().set(key(userId, weatherId), value, EXPIRATION);
    }

    public void delete(UUID userId, UUID weatherId) {
        redisTemplate.delete(key(userId, weatherId));
    }

    private String key(UUID userId, UUID weatherId) {
        return KEY_PREFIX + userId + ":" + weatherId;
    }
}
