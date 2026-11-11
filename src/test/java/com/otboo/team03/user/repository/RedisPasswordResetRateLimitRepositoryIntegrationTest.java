
package com.otboo.team03.user.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisPasswordResetRateLimitRepositoryIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactoryA;
    private LettuceConnectionFactory connectionFactoryB;

    private StringRedisTemplate redisTemplate;
    private RedisPasswordResetRateLimitRepository repositoryA;
    private RedisPasswordResetRateLimitRepository repositoryB;

    @BeforeEach
    void setUp() {
        String host = redis.getHost();
        int port = redis.getMappedPort(6379);

        connectionFactoryA = new LettuceConnectionFactory(host, port);
        connectionFactoryB = new LettuceConnectionFactory(host, port);

        connectionFactoryA.afterPropertiesSet();
        connectionFactoryB.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactoryA);

        repositoryA = new RedisPasswordResetRateLimitRepository(redisTemplate);
        repositoryB = new RedisPasswordResetRateLimitRepository(new StringRedisTemplate(connectionFactoryB));

        // 각 테스트에서 Redis 데이터를 독립적으로 검증
        try (RedisConnection connection = connectionFactoryA.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @AfterEach
    void tearDown() {
        connectionFactoryA.destroy();
        connectionFactoryB.destroy();
    }

    @Test
    @DisplayName("동일 이메일의 첫 요청은 허용하고 연속 요청은 차단한다")
    void tryAcquire_repeatedRequest_onlyFirstSucceeds() {
        // given
        String email = "user@example.com";

        // when
        Optional<String> first = repositoryA.tryAcquire(email);
        Optional<String> second = repositoryB.tryAcquire(email);

        // then
        assertThat(first).isPresent();
        assertThat(second).isEmpty();
    }

    @Test
    @DisplayName("요청 제한에 3분 TTL을 설정하고 만료 후 재요청을 허용한다")
    void tryAcquire_expiration_allowsNewRequest() {
        // given
        String email = "user@example.com";
        repositoryA.tryAcquire(email);

        Set<String> keys = redisTemplate.keys("auth:password-reset:limit:*");
        assertThat(keys).hasSize(1);

        String key = keys.iterator().next();

        // then: TTL 확인
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttl).isBetween(1L, Duration.ofMinutes(3).toSeconds());

        // when: Redis에서 TTL을 강제로 만료시킴
        redisTemplate.expire(key, Duration.ZERO);

        // then
        assertThat(repositoryB.tryAcquire(email)).isPresent();
    }

    @Test
    @DisplayName("다른 요청의 UUID로는 요청 제한을 해제할 수 없다")
    void release_wrongRequestId_doesNotDeleteLimit() {
        // given
        String email = "user@example.com";
        repositoryA.tryAcquire(email).orElseThrow();

        // when
        repositoryB.release(email, "invalid-request-id");

        // then
        assertThat(repositoryA.tryAcquire(email)).isEmpty();
    }

    @Test
    @DisplayName("만료된 요청의 UUID가 새 요청의 제한을 삭제하지 못한다")
    void release_expiredRequest_doesNotDeleteNewLimit() {
        // given
        String email = "user@example.com";
        String oldRequestId = repositoryA.tryAcquire(email).orElseThrow();

        String key = redisTemplate.keys("auth:password-reset:limit:*").iterator().next();
        redisTemplate.expire(key, Duration.ZERO);

        String newRequestId = repositoryB.tryAcquire(email).orElseThrow();

        // when: 이전 요청이 제한 해제를 시도
        repositoryA.release(email, oldRequestId);

        // then: 새로운 요청의 제한은 유지
        assertThat(repositoryA.tryAcquire(email)).isEmpty();

        // when: 실제 소유자가 제한 해제
        repositoryB.release(email, newRequestId);

        // then
        assertThat(repositoryA.tryAcquire(email)).isPresent();
    }

    @Test
    @DisplayName("서로 다른 인스턴스에서 동시에 요청해도 하나만 성공한다")
    void tryAcquire_concurrentRequests_onlyOneSucceeds() throws Exception {
        // given
        String email = "user@example.com";
        int requestCount = 8;

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                RedisPasswordResetRateLimitRepository repository = i % 2 == 0 ? repositoryA : repositoryB;

                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return repository.tryAcquire(email).isPresent();
                }));
            }

            // 모든 요청이 준비된 후 동시에 실행
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int successCount = 0;

            for (Future<Boolean> future : futures) {
                if (future.get(10, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }

            // then
            assertThat(successCount).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}
