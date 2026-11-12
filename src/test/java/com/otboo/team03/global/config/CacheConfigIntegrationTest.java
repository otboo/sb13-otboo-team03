package com.otboo.team03.global.config;

import com.otboo.team03.Sb13OtbooTeam03Application;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.cache.interceptor.CacheInterceptor;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.context.ApplicationContext;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = Sb13OtbooTeam03Application.class,
        properties = "otboo.cache.ttls.cache-infra-short-ttl=20s"
)
@ActiveProfiles("test")
@Testcontainers
@Import(CacheConfigIntegrationTest.TestCacheConfiguration.class)
class CacheConfigIntegrationTest {

    private static final String CACHE_NAME = "cache-infra-test";
    private static final String SHORT_TTL_CACHE_NAME = "cache-infra-short-ttl";
    private static final String CACHE_KEY_PREFIX = CacheNames.KEY_PREFIX + CACHE_NAME + "::";

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

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationContext applicationContext;

    private CacheErrorHandler cacheErrorHandler() {
        return applicationContext.getBean(CacheInterceptor.class).getErrorHandler();
    }

    @BeforeEach
    void setUp() {
        Set<String> keys = redisTemplate.keys(CacheNames.KEY_PREFIX + "cache-infra-*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        cacheService.reset();
    }

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

    @Test
    @DisplayName("캐시 무효화 후 재조회하면 원본 메서드가 다시 실행된다")
    void evict_existingCache_reloadsValue() {
        // given
        String key = "item-1";
        CachedValue first = cacheService.get(key);

        assertThat(cacheService.get(key)).isEqualTo(first);
        assertThat(cacheService.executionCount()).isEqualTo(1);

        // when
        cacheService.evict(key);
        CachedValue second = cacheService.get(key);

        // then
        assertThat(second).isNotEqualTo(first);
        assertThat(cacheService.executionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("캐시별로 설정한 TTL이 기본 TTL보다 우선 적용된다")
    void get_customCache_usesConfiguredTtl() {
        // given
        String key = "item-1";
        String redisKey = CacheNames.KEY_PREFIX + SHORT_TTL_CACHE_NAME + "::" + key;
        Cache cache = cacheManager.getCache(SHORT_TTL_CACHE_NAME);
        assertThat(cache).isNotNull();

        // when
        cache.put(key, new CachedValue(key, 1));

        // then
        assertThat(redisTemplate.hasKey(redisKey)).isTrue();
        assertThat(redisTemplate.getExpire(redisKey, TimeUnit.SECONDS)).isBetween(1L, 20L);
    }

    @Test
    @DisplayName("트랜잭션 커밋 이후 캐시 무효화가 반영된다")
    void evict_transactionCommitted_invalidatesCache() {
        // given
        Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();

        String key = "item-1";
        CachedValue value = new CachedValue(key, 1);
        cache.put(key, value);

        // when
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            cache.evict(key);
            assertThat(cache.get(key, CachedValue.class)).isEqualTo(value);
        });

        // then
        assertThat(cache.get(key)).isNull();
    }

    @Test
    @DisplayName("트랜잭션 롤백 시 캐시 무효화가 반영되지 않는다")
    void evict_transactionRolledBack_preservesCache() {
        // given
        Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();

        String key = "item-1";
        CachedValue value = new CachedValue(key, 1);
        cache.put(key, value);

        // when
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            cache.evict(key);
            status.setRollbackOnly();
        });

        // then
        assertThat(cache.get(key, CachedValue.class)).isEqualTo(value);
    }

    @Test
    @DisplayName("트랜잭션 커밋 이후 캐시 저장이 반영된다")
    void put_transactionCommitted_storesCache() {
        // given
        Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();

        String key = "item-1";
        CachedValue value = new CachedValue(key, 1);

        // when
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            cache.put(key, value);
            assertThat(cache.get(key)).isNull();
        });

        // then
        assertThat(cache.get(key, CachedValue.class)).isEqualTo(value);
    }

    @Test
    @DisplayName("Redis 연결 장애로 캐시 조회에 실패하면 예외를 무시한다")
    void get_redisConnectionFailure_allowsFallback() {
        // given
        Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();
        RuntimeException failure = new RedisConnectionFailureException("Redis 연결 실패");

        // when & then
        assertThatCode(() -> cacheErrorHandler().handleCacheGetError(failure, cache, "item-1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Redis 연결 장애로 캐시 저장에 실패하면 예외를 무시한다")
    void put_redisConnectionFailure_allowsFallback() {
        // given
        Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();
        RuntimeException failure = new RedisConnectionFailureException("Redis 연결 실패");

        // when & then
        assertThatCode(() -> cacheErrorHandler().handleCachePutError(failure, cache, "item-1", "value"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Redis 타임아웃으로 캐시 조회 및 저장에 실패하면 예외를 무시한다")
    void cacheOperation_timeout_allowsFallback() {
        // given
        Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();
        RuntimeException failure = new QueryTimeoutException("Redis 응답 타임아웃");

        // when & then
        assertThatCode(() -> cacheErrorHandler().handleCacheGetError(failure, cache, "item-1"))
                .doesNotThrowAnyException();
        assertThatCode(() -> cacheErrorHandler().handleCachePutError(failure, cache, "item-1", "value"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("캐시 조회 및 저장에서 예상하지 못한 예외는 전파한다")
    void cacheOperation_unexpectedFailure_propagates() {
        // given
        Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();
        RuntimeException failure = new IllegalStateException("예상하지 못한 오류");

        // when & then
        assertThatThrownBy(() -> cacheErrorHandler().handleCacheGetError(failure, cache, "item-1"))
                .isSameAs(failure);
        assertThatThrownBy(() -> cacheErrorHandler().handleCachePutError(failure, cache, "item-1", "value"))
                .isSameAs(failure);
    }

    @Test
    @DisplayName("캐시 무효화 실패 시 예외를 전파한다")
    void evict_redisConnectionFailure_propagates() {
        // given
        Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();
        RuntimeException failure = new RedisConnectionFailureException("Redis 연결 실패");

        // when & then
        assertThatThrownBy(() -> cacheErrorHandler().handleCacheEvictError(failure, cache, "item-1"))
                .isSameAs(failure);
    }

    @Test
    @DisplayName("캐시 전체 삭제 실패 시 예외를 전파한다")
    void clear_redisConnectionFailure_propagates() {
        // given
        Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();
        RuntimeException failure = new RedisConnectionFailureException("Redis 연결 실패");

        // when & then
        assertThatThrownBy(() -> cacheErrorHandler().handleCacheClearError(failure, cache))
                .isSameAs(failure);
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
