package com.otboo.team03.user.repository;


import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RedisTemporaryPasswordRepository {

    private static final String KEY_PREFIX = "auth:temp-password:";
    private static final Duration EXPIRATION = Duration.ofMinutes(3);

    // Redis에 저장된 임시 비밀번호 해시가 일치할 때만 원자적으로 삭제
    // 동시 로그인 요청에서 동일한 임시 비밀번호가 중복 사용되는 것을 방지
    private static final RedisScript<Long> CONSUME_SCRIPT = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public void save(String email, String encodedPassword) {
        redisTemplate.opsForValue().set(KEY_PREFIX + email, encodedPassword, EXPIRATION);
    }

    public Optional<String> findByEmail(String email) {
        Object value = redisTemplate.opsForValue().get(KEY_PREFIX + email);
        return value instanceof String password ? Optional.of(password) : Optional.empty();
    }

    public void delete(String email) {
        redisTemplate.delete(KEY_PREFIX + email);
    }

    public boolean consumeIfMatches(String email, String encodedPassword) {
        Long deleted = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(KEY_PREFIX + email),
                encodedPassword
        );

        return Long.valueOf(1L).equals(deleted);
    }
}
