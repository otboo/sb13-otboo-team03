
package com.otboo.team03.global.security.jwt;

import com.otboo.team03.global.config.RedisConfig;
import com.otboo.team03.global.redis.RedisLockProvider;
import com.otboo.team03.global.exception.jwt.InvalidJwtException;
import com.otboo.team03.global.redis.RedisLockProvider.RedisLockAcquisitionException;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;


@Testcontainers
class RedisJwtRegistryIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactoryA;
    private LettuceConnectionFactory connectionFactoryB;

    private RedisJwtRegistry registryA;
    private RedisJwtRegistry registryB;

    private UUID userId;

    @BeforeEach
    void setUp() {
        String host = redis.getHost();
        int port = redis.getMappedPort(6379);

        connectionFactoryA = new LettuceConnectionFactory(host, port);
        connectionFactoryB = new LettuceConnectionFactory(host, port);

        connectionFactoryA.afterPropertiesSet();
        connectionFactoryB.afterPropertiesSet();

        RedisConfig redisConfig = new RedisConfig();
        var serializer = redisConfig.redisSerializer();

        RedisTemplate<String, Object> templateA = redisConfig.redisTemplate(connectionFactoryA, serializer);
        RedisTemplate<String, Object> templateB = redisConfig.redisTemplate(connectionFactoryB, serializer);

        registryA = createRegistry(templateA, connectionFactoryA);
        registryB = createRegistry(templateB, connectionFactoryB);

        userId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        try {
            if (registryA != null && userId != null) {
                registryA.invalidateJwtInformationByUserId(userId);
            }
        } finally {
            if (connectionFactoryA != null) {
                connectionFactoryA.destroy();
            }
            if (connectionFactoryB != null) {
                connectionFactoryB.destroy();
            }
        }
    }

    private RedisJwtRegistry createRegistry(
            RedisTemplate<String, Object> template,
            LettuceConnectionFactory connectionFactory
    ) {
        RedisLockProvider lockProvider = new RedisLockProvider(new StringRedisTemplate(connectionFactory));
        RedisJwtRegistry registry = new RedisJwtRegistry(mock(JwtTokenProvider.class), template, lockProvider);

        ReflectionTestUtils.setField(registry, "accessTokenValidity", Duration.ofMinutes(30));
        ReflectionTestUtils.setField(registry, "refreshTokenValidity", Duration.ofDays(7));

        return registry;
    }

    private JwtInformation createJwtInformation() {
        UserDto userDto = new UserDto(
                userId,
                Instant.now(),
                "test@example.com",
                "테스트 사용자",
                UserRole.USER,
                false
        );

        return new JwtInformation(
                userDto,
                "access-" + UUID.randomUUID(),
                "refresh-" + UUID.randomUUID()
        );
    }

    @Nested
    @DisplayName("인스턴스 간 JWT 공유")
    class JwtSharingTest {

        @Test
        @DisplayName("한 인스턴스에서 등록한 JWT를 다른 인스턴스에서도 조회할 수 있다")
        void registerJwtInformation_sharedAcrossInstances() {
            // given
            JwtInformation information = createJwtInformation();

            // when
            registryA.registerJwtInformation(information);

            // then
            assertThat(registryB.hasActiveJwtInformationByUserId(userId)).isTrue();
            assertThat(registryB.hasActiveJwtInformationByAccessToken(information.getAccessToken())).isTrue();
            assertThat(registryB.hasActiveJwtInformationByRefreshToken(information.getRefreshToken())).isTrue();
        }

        @Test
        @DisplayName("다른 인스턴스에서 동일 사용자 JWT를 재등록하면 이전 토큰이 무효화된다")
        void registerJwtInformation_newLoginInvalidatesOldTokens() {
            // given
            JwtInformation oldInformation = createJwtInformation();
            registryA.registerJwtInformation(oldInformation);

            JwtInformation newInformation = createJwtInformation();

            // when
            registryB.registerJwtInformation(newInformation);

            // then
            assertThat(registryA.hasActiveJwtInformationByAccessToken(oldInformation.getAccessToken())).isFalse();
            assertThat(registryA.hasActiveJwtInformationByRefreshToken(oldInformation.getRefreshToken())).isFalse();

            assertThat(registryA.hasActiveJwtInformationByAccessToken(newInformation.getAccessToken())).isTrue();
            assertThat(registryA.hasActiveJwtInformationByRefreshToken(newInformation.getRefreshToken())).isTrue();
            assertThat(registryA.hasActiveJwtInformationByUserId(userId)).isTrue();
        }

    }

    @Nested
    @DisplayName("인스턴스 간 JWT 무효화")
    class JwtInvalidationTest {

        @Test
        @DisplayName("다른 인스턴스에서 사용자 JWT를 무효화하면 모든 인스턴스에 반영된다")
        void invalidateJwtInformationByUserId_sharedAcrossInstances() {
            // given
            JwtInformation information = createJwtInformation();
            registryA.registerJwtInformation(information);

            assertThat(registryB.hasActiveJwtInformationByAccessToken(information.getAccessToken())).isTrue();

            // when
            registryB.invalidateJwtInformationByUserId(userId);

            // then
            assertThat(registryA.hasActiveJwtInformationByUserId(userId)).isFalse();
            assertThat(registryA.hasActiveJwtInformationByAccessToken(information.getAccessToken())).isFalse();
            assertThat(registryA.hasActiveJwtInformationByRefreshToken(information.getRefreshToken())).isFalse();
        }

        @Test
        @DisplayName("다른 인스턴스에서 로그아웃하면 기존 JWT가 모든 인스턴스에서 무효화된다")
        void invalidateJwtInformationByRefreshToken_sharedAcrossInstances() {
            // given
            JwtInformation information = createJwtInformation();
            registryA.registerJwtInformation(information);

            assertThat(registryB.hasActiveJwtInformationByRefreshToken(information.getRefreshToken())).isTrue();

            // when
            registryB.invalidateJwtInformationByRefreshToken(information.getRefreshToken());

            // then
            assertThat(registryA.hasActiveJwtInformationByUserId(userId)).isFalse();
            assertThat(registryA.hasActiveJwtInformationByAccessToken(information.getAccessToken())).isFalse();
            assertThat(registryA.hasActiveJwtInformationByRefreshToken(information.getRefreshToken())).isFalse();
        }

        @Test
        @DisplayName("한 사용자의 JWT를 무효화해도 다른 사용자의 JWT는 유지된다")
        void invalidateJwtInformationByRefreshToken_doesNotAffectOtherUsers() {
            // given
            JwtInformation informationA = createJwtInformation();
            registryA.registerJwtInformation(informationA);

            UUID otherUserId = UUID.randomUUID();
            UserDto otherUserDto = new UserDto(
                    otherUserId,
                    Instant.now(),
                    "other@example.com",
                    "다른 사용자",
                    UserRole.USER,
                    false
            );

            JwtInformation informationB = new JwtInformation(
                    otherUserDto,
                    "access-" + UUID.randomUUID(),
                    "refresh-" + UUID.randomUUID()
            );

            try {
                registryB.registerJwtInformation(informationB);

                // when
                registryA.invalidateJwtInformationByRefreshToken(informationA.getRefreshToken());

                // then
                assertThat(registryB.hasActiveJwtInformationByUserId(userId)).isFalse();
                assertThat(registryB.hasActiveJwtInformationByAccessToken(informationA.getAccessToken())).isFalse();
                assertThat(registryB.hasActiveJwtInformationByRefreshToken(informationA.getRefreshToken())).isFalse();

                assertThat(registryB.hasActiveJwtInformationByUserId(otherUserId)).isTrue();
                assertThat(registryB.hasActiveJwtInformationByAccessToken(informationB.getAccessToken())).isTrue();
                assertThat(registryB.hasActiveJwtInformationByRefreshToken(informationB.getRefreshToken())).isTrue();

            } finally {
                registryB.invalidateJwtInformationByUserId(otherUserId);
            }
        }
    }

    @Nested
    @DisplayName("인스턴스 간 JWT 갱신")
    class JwtRotationTest {

        private boolean attemptRotation(
                RedisJwtRegistry registry,
                String refreshToken,
                JwtInformation newInformation,
                CountDownLatch ready,
                CountDownLatch start
        ) throws InterruptedException {
            ready.countDown();
            start.await();

            try {
                registry.rotateJwtInformation(refreshToken, newInformation);
                return true;
            } catch (InvalidJwtException | RedisLockAcquisitionException e) {
                return false;
            }
        }

        @Test
        @DisplayName("다른 인스턴스에서 JWT를 갱신하면 이전 토큰은 무효화되고 새 토큰은 공유된다")
        void rotateJwtInformation_sharedAcrossInstances() {
            // given
            JwtInformation information = createJwtInformation();
            registryA.registerJwtInformation(information);

            String oldAccessToken = information.getAccessToken();
            String oldRefreshToken = information.getRefreshToken();

            JwtInformation newInformation = new JwtInformation(
                    information.getUserDto(),
                    "new-access-" + UUID.randomUUID(),
                    "new-refresh-" + UUID.randomUUID()
            );

            // when
            registryB.rotateJwtInformation(oldRefreshToken, newInformation);

            // then
            assertThat(registryA.hasActiveJwtInformationByAccessToken(oldAccessToken)).isFalse();
            assertThat(registryA.hasActiveJwtInformationByRefreshToken(oldRefreshToken)).isFalse();
            assertThat(registryA.hasActiveJwtInformationByAccessToken(newInformation.getAccessToken())).isTrue();
            assertThat(registryA.hasActiveJwtInformationByRefreshToken(newInformation.getRefreshToken())).isTrue();
            assertThat(registryA.hasActiveJwtInformationByUserId(userId)).isTrue();
        }

        @Test
        @DisplayName("동일한 Refresh Token을 동시에 갱신하면 하나의 요청만 성공한다")
        void rotateJwtInformation_concurrentRequests_onlyOneSucceeds() throws Exception {
            // given
            JwtInformation information = createJwtInformation();
            registryA.registerJwtInformation(information);

            String oldAccessToken = information.getAccessToken();
            String oldRefreshToken = information.getRefreshToken();

            JwtInformation newInformationA = new JwtInformation(
                    information.getUserDto(),
                    "access-a-" + UUID.randomUUID(),
                    "refresh-a-" + UUID.randomUUID()
            );

            JwtInformation newInformationB = new JwtInformation(
                    information.getUserDto(),
                    "access-b-" + UUID.randomUUID(),
                    "refresh-b-" + UUID.randomUUID()
            );

            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            try {
                Future<Boolean> resultA = executor.submit(
                        () -> attemptRotation(registryA, oldRefreshToken, newInformationA, ready, start)
                );
                Future<Boolean> resultB = executor.submit(
                        () -> attemptRotation(registryB, oldRefreshToken, newInformationB, ready, start)
                );

                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

                // when: 두 인스턴스에서 동시에 Rotation 시작
                start.countDown();

                boolean successA = resultA.get(10, TimeUnit.SECONDS);
                boolean successB = resultB.get(10, TimeUnit.SECONDS);

                // then: 두 요청 중 하나만 성공
                assertThat(successA).isNotEqualTo(successB);

                // 기존 토큰은 무효화
                assertThat(registryA.hasActiveJwtInformationByAccessToken(oldAccessToken)).isFalse();
                assertThat(registryB.hasActiveJwtInformationByRefreshToken(oldRefreshToken)).isFalse();

                // 성공한 요청의 토큰만 활성화
                JwtInformation successful = successA ? newInformationA : newInformationB;
                JwtInformation failed = successA ? newInformationB : newInformationA;

                assertThat(registryA.hasActiveJwtInformationByAccessToken(successful.getAccessToken())).isTrue();
                assertThat(registryB.hasActiveJwtInformationByRefreshToken(successful.getRefreshToken())).isTrue();

                assertThat(registryA.hasActiveJwtInformationByAccessToken(failed.getAccessToken())).isFalse();
                assertThat(registryB.hasActiveJwtInformationByRefreshToken(failed.getRefreshToken())).isFalse();

            } finally {
                start.countDown();
                executor.shutdownNow();
            }
        }
    }
}
