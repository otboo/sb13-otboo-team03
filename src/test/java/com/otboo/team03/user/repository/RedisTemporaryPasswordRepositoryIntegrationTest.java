
package com.otboo.team03.user.repository;

import com.otboo.team03.global.config.RedisConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisTemporaryPasswordRepositoryIntegrationTest {

    private static final String EMAIL = "user@example.com";
    private static final String ENCODED_PASSWORD = "encodedTemporaryPassword";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static RedisTemporaryPasswordRepository repository;

    @BeforeAll
    static void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();

        RedisConfig redisConfig = new RedisConfig();
        repository = new RedisTemporaryPasswordRepository(
                redisConfig.redisTemplate(connectionFactory, redisConfig.redisSerializer())
        );
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void clearRedis() {
        repository.delete(EMAIL);
    }

    @Nested
    @DisplayName("임시 비밀번호 일회성 소모")
    class ConsumeIfMatchesTest {

        @Test
        @DisplayName("성공 - 저장된 해시가 일치하면 삭제하고 한 번만 성공한다")
        void consumeIfMatches_matchingPassword_succeedsOnce() {
            // given
            repository.save(EMAIL, ENCODED_PASSWORD);

            // when
            boolean first = repository.consumeIfMatches(EMAIL, ENCODED_PASSWORD);
            boolean second = repository.consumeIfMatches(EMAIL, ENCODED_PASSWORD);

            // then
            assertThat(first).isTrue();
            assertThat(second).isFalse();
            assertThat(repository.findByEmail(EMAIL)).isEmpty();
        }

        @Test
        @DisplayName("실패 - 해시가 일치하지 않으면 기존 임시 비밀번호를 유지한다")
        void consumeIfMatches_wrongPassword_preservesTemporaryPassword() {
            // given
            repository.save(EMAIL, ENCODED_PASSWORD);

            // when
            boolean result = repository.consumeIfMatches(EMAIL, "wrongEncodedPassword");

            // then
            assertThat(result).isFalse();
            assertThat(repository.findByEmail(EMAIL)).contains(ENCODED_PASSWORD);
        }

        @Test
        @DisplayName("실패 - 임시 비밀번호가 없으면 소모에 실패한다")
        void consumeIfMatches_missingPassword_returnsFalse() {
            // when
            boolean result = repository.consumeIfMatches(EMAIL, ENCODED_PASSWORD);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("동시 요청 - 동일한 임시 비밀번호는 하나의 요청만 소모할 수 있다")
        void consumeIfMatches_concurrentRequests_onlyOneSucceeds() throws Exception {
            // given
            repository.save(EMAIL, ENCODED_PASSWORD);

            int requestCount = 8;
            ExecutorService executor = Executors.newFixedThreadPool(requestCount);
            CountDownLatch readyLatch = new CountDownLatch(requestCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            try {
                List<Future<Boolean>> futures = new ArrayList<>();

                for (int i = 0; i < requestCount; i++) {
                    futures.add(executor.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();
                        return repository.consumeIfMatches(EMAIL, ENCODED_PASSWORD);
                    }));
                }

                assertThat(readyLatch.await(10, TimeUnit.SECONDS)).isTrue();
                startLatch.countDown();

                // when
                int successCount = 0;
                for (Future<Boolean> future : futures) {
                    if (future.get(10, TimeUnit.SECONDS)) {
                        successCount++;
                    }
                }

                // then
                assertThat(successCount).isEqualTo(1);
                assertThat(repository.findByEmail(EMAIL)).isEmpty();

            } finally {
                startLatch.countDown();
                executor.shutdownNow();
            }
        }
    }
}
