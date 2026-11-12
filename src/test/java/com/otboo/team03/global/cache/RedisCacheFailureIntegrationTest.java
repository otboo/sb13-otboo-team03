
package com.otboo.team03.global.cache;

import com.otboo.team03.global.config.CacheConfig;
import com.otboo.team03.global.config.RedisConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(RedisCacheFailureIntegrationTest.TestConfiguration.class)
@TestPropertySource(properties = "otboo.cache.default-ttl=5m")
class RedisCacheFailureIntegrationTest {

    private static final String CACHE_NAME = "cache-failure-test";

    @Autowired
    private TestCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService.reset();
    }

    @Test
    @DisplayName("Redis 조회 장애 시 캐시를 사용하지 않고 원본 메서드를 실행한다")
    void get_redisFailure_executesOriginalMethod() {
        // when
        String first = cacheService.get("item-1");
        String second = cacheService.get("item-1");

        // then
        assertThat(first).isEqualTo("item-1-1");
        assertThat(second).isEqualTo("item-1-2");
        assertThat(cacheService.executionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Redis 저장 장애 시 원본 메서드의 결과를 정상 반환한다")
    void put_redisFailure_returnsOriginalResult() {
        // when
        String result = cacheService.update("item-1");

        // then
        assertThat(result).isEqualTo("updated-item-1");
    }

    @Test
    @DisplayName("Redis 장애로 캐시 무효화에 실패하면 예외를 전파한다")
    void evict_redisFailure_propagatesException() {
        // when & then
        assertThatThrownBy(() -> cacheService.evict("item-1"))
                .isInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    @DisplayName("Redis 장애로 캐시 전체 삭제에 실패하면 예외를 전파한다")
    void clear_redisFailure_propagatesException() {
        // when & then
        assertThatThrownBy(cacheService::clear)
                .isInstanceOf(RedisConnectionFailureException.class);
    }

    @Configuration(proxyBeanMethods = false)
    @Import({CacheConfig.class, RedisConfig.class})
    static class TestConfiguration {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
            when(factory.getConnection())
                    .thenThrow(new RedisConnectionFailureException("Redis 연결 실패"));
            return factory;
        }

        @Bean
        TestCacheService testCacheService() {
            return new TestCacheService();
        }
    }

    public static class TestCacheService {

        private final AtomicInteger executions = new AtomicInteger();

        @Cacheable(cacheNames = CACHE_NAME, key = "#p0")
        public String get(String id) {
            return id + "-" + executions.incrementAndGet();
        }

        @CachePut(cacheNames = CACHE_NAME, key = "#p0")
        public String update(String id) {
            return "updated-" + id;
        }

        @CacheEvict(cacheNames = CACHE_NAME, key = "#p0")
        public void evict(String id) {
        }

        @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
        public void clear() {
        }

        public int executionCount() {
            return executions.get();
        }

        public void reset() {
            executions.set(0);
        }
    }
}
