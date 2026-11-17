package com.otboo.team03.recommendation.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class RedisRecommendationHistoryRepository {

    private static final String KEY_PREFIX = "recommendation:previous:";
    private static final Duration EXPIRATION = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;

    public Set<UUID> find(UUID userId, UUID weatherId) {
        String value;
        try {
            value = redisTemplate.opsForValue().get(key(userId, weatherId));
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            log.warn("직전 추천 기록 조회 실패, 기록 없이 추천: userId={}, weatherId={}", userId, weatherId, e);
            return Set.of();
        }
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
        try {
            redisTemplate.opsForValue().set(key(userId, weatherId), value, EXPIRATION);
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            log.warn("직전 추천 기록 저장 실패, 추천 결과 유지: userId={}, weatherId={}", userId, weatherId, e);
        }
    }

    public void delete(UUID userId, UUID weatherId) {
        try {
            redisTemplate.delete(key(userId, weatherId));
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            log.warn("직전 추천 기록 삭제 실패, 빈 추천 결과 유지: userId={}, weatherId={}", userId, weatherId, e);
        }
    }

    private String key(UUID userId, UUID weatherId) {
        return KEY_PREFIX + userId + ":" + weatherId;
    }
}
