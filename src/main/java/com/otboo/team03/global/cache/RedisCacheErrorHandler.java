package com.otboo.team03.global.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;

@Slf4j
public class RedisCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        if (isRecoverableFailure(exception)) {
            log.warn("Redis 캐시 조회 실패, 원본 메서드 실행: cache={}, key={}", cache.getName(), key);
            return;
        }

        throw exception;
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        if (isRecoverableFailure(exception)) {
            log.warn("Redis 캐시 저장 실패, 원본 결과 반환: cache={}, key={}", cache.getName(), key);
            return;
        }

        throw exception;
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.error("Redis 캐시 무효화 실패: cache={}, key={}", cache.getName(), key, exception);
        throw exception;
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.error("Redis 캐시 전체 삭제 실패: cache={}", cache.getName(), exception);
        throw exception;
    }

    private boolean isRecoverableFailure(RuntimeException exception) {
        return exception instanceof RedisConnectionFailureException || exception instanceof QueryTimeoutException;
    }
}
