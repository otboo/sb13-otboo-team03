
package com.otboo.team03.global.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class RedisLockProvider {

    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(10);
    private static final String LOCK_KEY_PREFIX = "lock:";

    // 락 소유권을 확인한 후 해제하는 Lua 스크립트
    // GET과 DEL을 원자적으로 실행하여 다른 요청이 획득한 락이 삭제되는 것을 방지
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                        return redis.call('del', KEYS[1])
                    else
                        return 0
                    end
                    """,
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;

    public String acquireLock(String key) {
        String lockKey = LOCK_KEY_PREFIX + key;
        String lockValue = UUID.randomUUID().toString();

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_TIMEOUT);

        if (Boolean.TRUE.equals(acquired)) {
            log.debug("분산 락 획득 성공: {}", lockKey);
            return lockValue;
        }

        log.debug("분산 락 획득 실패: {}", lockKey);
        throw new RedisLockAcquisitionException("분산 락 획득 실패: " + lockKey);
    }

    public void releaseLock(String key, String lockValue) {
        String lockKey = LOCK_KEY_PREFIX + key;

        try {
            Long released = redisTemplate.execute(
                    UNLOCK_SCRIPT,
                    List.of(lockKey),
                    lockValue
            );

            if (Long.valueOf(1L).equals(released)) {
                log.debug("분산 락 해제 완료: {}", lockKey);
            } else {
                log.debug("분산 락 해제 생략 (소유권 없음): {}", lockKey);
            }
        } catch (Exception e) {
            log.warn("분산 락 해제 실패: {}", lockKey, e);
        }
    }

    public static class RedisLockAcquisitionException extends RuntimeException {

        public RedisLockAcquisitionException(String message) {
            super(message);
        }
    }
}
