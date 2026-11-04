package com.otboo.team03.user.repository;


import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RedisTemporaryPasswordRepository {

    private static final String KEY_PREFIX = "auth:temp-password:";
    private static final Duration EXPIRATION = Duration.ofMinutes(3);

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
}
