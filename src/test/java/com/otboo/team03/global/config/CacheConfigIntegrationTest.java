package com.otboo.team03.global.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Import(CacheConfigIntegrationTest.TestCacheConfiguration.class)
class CacheConfigIntegrationTest {

    private static final String CACHE_NAME = "cache-infra-test";
    private static final String CACHE_KEY_PREFIX = "otboo:cache:cache-infra-test::";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private TestCacheService cacheService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        Set<String> keys = redisTemplate.keys(CACHE_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        cacheService.reset();
    }

    @Nested
    @DisplayName("캐시 조회 및 저장")
    class CacheReadTest {

        @Test
        @DisplayName("동일한 키를 재조회하면 캐시를 반환하고 원본 메서드는 한 번만 실행한다")
        void get_sameKey_returnsCachedValue() {
            // given
            String key = "item-1";

            // when
            CachedValue first = cacheService.get(key);
            CachedValue second = cacheService.get(key);

            // then
            assertThat(second).isEqualTo(first);
            assertThat(cacheService.executionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("캐시 키에 공통 접두사와 기본 TTL 5분이 적용된다")
        void get_cachedValue_usesPrefixAndDefaultTtl() {
            // given
            String key = "item-1";
            String redisKey = CACHE_KEY_PREFIX + key;

            // when
            cacheService.get(key);

            // then
            assertThat(redisTemplate.hasKey(redisKey)).isTrue();
            assertThat(redisTemplate.getExpire(redisKey, TimeUnit.SECONDS)).isBetween(1L, 300L);
        }
    }

    @Nested
    @DisplayName("캐시 무효화")
    class CacheEvictionTest {

        @Test
        @DisplayName("캐시 무효화 후 재조회하면 원본 메서드가 다시 실행된다")
        void evict_existingCache_reloadsValue() {
            // given
            String key = "item-1";
            CachedValue first = cacheService.get(key);
            cacheService.get(key);

            assertThat(cacheService.executionCount()).isEqualTo(1);

            // when
            cacheService.evict(key);
            CachedValue second = cacheService.get(key);

            // then
            assertThat(second).isNotEqualTo(first);
            assertThat(cacheService.executionCount()).isEqualTo(2);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestCacheConfiguration {

        @Bean
        TestCacheService testCacheService() {
            return new TestCacheService();
        }
    }

    public record CachedValue(String id, int version) {
    }

    public static class TestCacheService {

        private final AtomicInteger executions = new AtomicInteger();

        @Cacheable(cacheNames = CACHE_NAME, key = "#p0")
        public CachedValue get(String id) {
            return new CachedValue(id, executions.incrementAndGet());
        }

        @CacheEvict(cacheNames = CACHE_NAME, key = "#p0")
        public void evict(String id) {
        }

        public int executionCount() {
            return executions.get();
        }

        public void reset() {
            executions.set(0);
        }
    }
}
