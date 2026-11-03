
package com.otboo.team03.global.redis;

import com.otboo.team03.global.redis.RedisLockProvider.RedisLockAcquisitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class RedisLockProviderTest {

    private static final String LOCK_KEY = "user-id-123";
    private static final String REDIS_LOCK_KEY = "lock:" + LOCK_KEY;
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(10);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisLockProvider redisLockProvider;

    @BeforeEach
    void setUp() {
        redisLockProvider = new RedisLockProvider(redisTemplate);
    }

    @Test
    void acquireLock_success() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        given(valueOperations.setIfAbsent(
                eq(REDIS_LOCK_KEY),
                anyString(),
                eq(LOCK_TIMEOUT)
        )).willReturn(true);

        // when
        String lockValue = redisLockProvider.acquireLock(LOCK_KEY);

        // then
        assertThat(lockValue).isNotBlank();

        assertThatCode(() -> UUID.fromString(lockValue))
                .doesNotThrowAnyException();

        then(valueOperations).should()
                .setIfAbsent(
                        eq(REDIS_LOCK_KEY),
                        eq(lockValue),
                        eq(LOCK_TIMEOUT)
                );
    }

    @Test
    void acquireLock_alreadyLocked_throwsException() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        given(valueOperations.setIfAbsent(
                eq(REDIS_LOCK_KEY),
                anyString(),
                eq(LOCK_TIMEOUT)
        )).willReturn(false);

        // when & then
        assertThatThrownBy(() -> redisLockProvider.acquireLock(LOCK_KEY))
                .isInstanceOf(RedisLockAcquisitionException.class)
                .hasMessageContaining(REDIS_LOCK_KEY);
    }

    @Test
    void releaseLock_success() {
        // given
        String lockValue = UUID.randomUUID().toString();

        given(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(REDIS_LOCK_KEY)),
                eq(lockValue)
        )).willReturn(1L);

        // when
        redisLockProvider.releaseLock(LOCK_KEY, lockValue);

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<RedisScript<Long>> scriptCaptor =
                ArgumentCaptor.forClass(RedisScript.class);

        then(redisTemplate).should()
                .execute(
                        scriptCaptor.capture(),
                        eq(List.of(REDIS_LOCK_KEY)),
                        eq(lockValue)
                );

        RedisScript<Long> script = scriptCaptor.getValue();

        assertThat(script.getResultType()).isEqualTo(Long.class);

        // 실제 Lua 스크립트가 소유권 비교 후 삭제하는 구조인지 확인
        assertThat(script.getScriptAsString())
                .contains("redis.call('get', KEYS[1]) == ARGV[1]")
                .contains("redis.call('del', KEYS[1])");
    }

    @Test
    void releaseLock_redisException_doesNotThrow() {
        // given
        String lockValue = UUID.randomUUID().toString();

        given(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(REDIS_LOCK_KEY)),
                eq(lockValue)
        )).willThrow(new RuntimeException("Redis connection failed"));

        // when & then
        assertThatCode(
                () -> redisLockProvider.releaseLock(LOCK_KEY, lockValue)
        ).doesNotThrowAnyException();

        then(redisTemplate).should()
                .execute(
                        any(RedisScript.class),
                        eq(List.of(REDIS_LOCK_KEY)),
                        eq(lockValue)
                );
    }
}
