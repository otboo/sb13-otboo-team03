package com.otboo.team03.recommendation.repository;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@Slf4j
public class RedisRecommendationHistoryRepository {
    private static final String KEY_PREFIX = "recommendation:previous:";
    private static final String RECENT_PREFIX = "recommendation:recent:";
    private static final Duration EXPIRATION = Duration.ofMinutes(30);
    private static final int LIMIT = 5;
    private static final RedisScript<List> READ = RedisScript.of("""
            local values = {}
            if redis.call('TYPE', KEYS[1]).ok == 'list' then values = redis.call('LRANGE', KEYS[1], 0, 4) end
            local previous = false
            if redis.call('TYPE', KEYS[2]).ok == 'string' then previous = redis.call('GET', KEYS[2]) end
            if previous and previous ~= '' and values[1] ~= previous then
                table.insert(values, 1, previous)
            end
            while #values > 5 do table.remove(values) end
            return values
            """, List.class);
    private static final RedisScript<Long> SAVE = RedisScript.of("""
            local recentType = redis.call('TYPE', KEYS[1]).ok
            local oldType = redis.call('TYPE', KEYS[2]).ok
            if (recentType ~= 'none' and recentType ~= 'list') or (oldType ~= 'none' and oldType ~= 'string') then return 0 end
            local previous = redis.call('GET', KEYS[2])
            if previous and previous ~= '' and redis.call('LINDEX', KEYS[1], 0) ~= previous then
                redis.call('LPUSH', KEYS[1], previous)
            end
            redis.call('LPUSH', KEYS[1], ARGV[1])
            redis.call('LTRIM', KEYS[1], 0, 4)
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[2])
            return 1
            """, Long.class);
    private final StringRedisTemplate redisTemplate;

    public List<Set<UUID>> findRecent(UUID userId, UUID weatherId) {
        try {
            List<?> values = redisTemplate.execute(READ, List.of(recentKey(userId, weatherId), key(userId, weatherId)));
            if (values == null) return List.of();
            return values.stream().filter(String.class::isInstance).map(String.class::cast)
                    .map(this::parse).filter(ids -> !ids.isEmpty()).limit(LIMIT).toList();
        } catch (RedisConnectionFailureException | QueryTimeoutException exception) { failed("최근 이력 조회", exception); return List.of(); }
    }
    public void save(UUID userId, UUID weatherId, Set<UUID> clothesIds) {
        if (clothesIds.isEmpty()) { delete(userId, weatherId); return; }
        String value = clothesIds.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
        try {
            redisTemplate.execute(SAVE, List.of(recentKey(userId, weatherId), key(userId, weatherId)),
                    value, Long.toString(EXPIRATION.toSeconds()));
        } catch (RedisConnectionFailureException | QueryTimeoutException exception) { failed("저장", exception); }
    }
    public void delete(UUID userId, UUID weatherId) {
        try { redisTemplate.delete(List.of(key(userId, weatherId), recentKey(userId, weatherId))); }
        catch (RedisConnectionFailureException | QueryTimeoutException exception) { failed("삭제", exception); }
    }
    private Set<UUID> parse(String value) {
        try { return parseStrict(value); }
        catch (IllegalArgumentException exception) {
            log.warn("잘못된 추천 이력 무시");
            return Set.of();
        }
    }
    private Set<UUID> parseStrict(String value) {
        if (value == null || value.isEmpty()) return Set.of();
        return Arrays.stream(value.split(",", -1)).map(UUID::fromString).collect(Collectors.toUnmodifiableSet());
    }
    private void failed(String operation, DataAccessException exception) {
        log.warn("추천 이력 {} 실패, 추천은 계속 진행: errorType={}", operation, exception.getClass().getSimpleName());
    }
    private String key(UUID userId, UUID weatherId) { return KEY_PREFIX + userId + ":" + weatherId; }
    private String recentKey(UUID userId, UUID weatherId) { return RECENT_PREFIX + userId + ":" + weatherId; }
}
