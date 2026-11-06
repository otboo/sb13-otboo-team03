
package com.otboo.team03.global.security.jwt;

import com.otboo.team03.global.config.RetryConfig;
import com.otboo.team03.global.redis.RedisLockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(classes = {RetryConfig.class, RedisJwtRegistryRetryTest.TestConfig.class})
@TestPropertySource(properties = {
        "otboo.jwt.access-token-validity=PT30M",
        "otboo.jwt.refresh-token-validity=P7D"
})
class RedisJwtRegistryRetryTest {

    @Autowired
    private JwtRegistry jwtRegistry;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisLockProvider redisLockProvider;

    @Autowired
    private ListOperations<String, Object> listOperations;

    @BeforeEach
    void setUp() {
        reset(redisTemplate, redisLockProvider, listOperations);
    }

    @Nested
    @DisplayName("사용자 JWT 세션 무효화 재시도")
    class InvalidateRetryTest {

        @Test
        @DisplayName("Redis 일시적 연결 실패 후 재시도하여 세션 무효화에 성공한다")
        void invalidateJwtInformationByUserId_connectionFailure_retriesSuccessfully() {
            // given
            UUID userId = UUID.randomUUID();
            String userKey = "jwt:user:" + userId;

            when(redisLockProvider.acquireLock(userId.toString())).thenReturn("lock-value");
            when(redisTemplate.opsForList()).thenReturn(listOperations);
            when(listOperations.range(userKey, 0, -1))
                    .thenThrow(new RedisConnectionFailureException("일시적인 Redis 연결 실패"))
                    .thenReturn(List.of());

            // when & then
            assertThat(AopUtils.isAopProxy(jwtRegistry)).isTrue();

            assertThatCode(() -> jwtRegistry.invalidateJwtInformationByUserId(userId))
                    .doesNotThrowAnyException();

            verify(listOperations, times(2)).range(userKey, 0, -1);
            verify(redisLockProvider, times(2)).acquireLock(userId.toString());
            verify(redisLockProvider, times(2)).releaseLock(userId.toString(), "lock-value");
            verify(redisTemplate).delete(userKey);
        }

        @Test
        @DisplayName("Redis 연결 장애가 지속되면 3회 시도 후 예외를 전파한다")
        void invalidateJwtInformationByUserId_connectionFailure_exhaustsRetries() {
            // given
            UUID userId = UUID.randomUUID();
            String userKey = "jwt:user:" + userId;

            when(redisLockProvider.acquireLock(userId.toString())).thenReturn("lock-value");
            when(redisTemplate.opsForList()).thenReturn(listOperations);
            when(listOperations.range(userKey, 0, -1))
                    .thenThrow(new RedisConnectionFailureException("Redis 연결 실패"));

            // when & then
            assertThatThrownBy(() -> jwtRegistry.invalidateJwtInformationByUserId(userId))
                    .isInstanceOf(RedisConnectionFailureException.class);

            verify(listOperations, times(3)).range(userKey, 0, -1);
            verify(redisLockProvider, times(3)).acquireLock(userId.toString());
            verify(redisLockProvider, times(3)).releaseLock(userId.toString(), "lock-value");
        }
    }

    @Configuration
    static class TestConfig {

        @Bean
        static ConversionService conversionService() {
            return ApplicationConversionService.getSharedInstance();
        }

        @Bean
        JwtTokenProvider jwtTokenProvider() {
            return mock(JwtTokenProvider.class);
        }

        @Bean
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate() {
            return mock(RedisTemplate.class);
        }

        @Bean
        RedisLockProvider redisLockProvider() {
            return mock(RedisLockProvider.class);
        }

        @Bean
        @SuppressWarnings("unchecked")
        ListOperations<String, Object> listOperations() {
            return mock(ListOperations.class);
        }

        @Bean
        RedisJwtRegistry redisJwtRegistry(
                JwtTokenProvider jwtTokenProvider,
                RedisTemplate<String, Object> redisTemplate,
                RedisLockProvider redisLockProvider
        ) {
            return new RedisJwtRegistry(jwtTokenProvider, redisTemplate, redisLockProvider);
        }
    }
}
