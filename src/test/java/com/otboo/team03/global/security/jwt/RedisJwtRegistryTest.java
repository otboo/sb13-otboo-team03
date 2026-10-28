
package com.otboo.team03.global.security.jwt;

import com.otboo.team03.global.exception.jwt.InvalidJwtException;
import com.otboo.team03.global.redis.RedisLockProvider;
import com.otboo.team03.user.dto.response.UserDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class RedisJwtRegistryTest {

    private static final Duration ACCESS_VALIDITY = Duration.ofMinutes(30);
    private static final Duration REFRESH_VALIDITY = Duration.ofDays(7);

    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    private static final String NEW_ACCESS_TOKEN = "new-access-token";
    private static final String NEW_REFRESH_TOKEN = "new-refresh-token";

    private static final String LOCK_VALUE = "lock-value";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RedisLockProvider redisLockProvider;

    @Mock
    private ListOperations<String, Object> listOperations;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private RedisJwtRegistry jwtRegistry;

    private UUID userId;
    private String userKey;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userKey = "jwt:user:" + userId;

        ReflectionTestUtils.setField(
                jwtRegistry, "accessTokenValidity", ACCESS_VALIDITY
        );

        ReflectionTestUtils.setField(
                jwtRegistry, "refreshTokenValidity", REFRESH_VALIDITY
        );
    }

    private JwtInformation createJwtInformation(
            String accessToken,
            String refreshToken
    ) {
        return new JwtInformation(
                mock(UserDto.class),
                accessToken,
                refreshToken
        );
    }

    @Nested
    class RegisterTest {

        @Test
        void registerJwtInformation_success() {
            // given
            JwtInformation information =
                    createJwtInformation(ACCESS_TOKEN, REFRESH_TOKEN);

            given(information.getUserDto().id()).willReturn(userId);
            given(redisLockProvider.acquireLock(userId.toString()))
                    .willReturn(LOCK_VALUE);

            given(redisTemplate.opsForList()).willReturn(listOperations);
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(listOperations.size(userKey)).willReturn(0L);

            // when
            jwtRegistry.registerJwtInformation(information);

            // then
            then(listOperations).should()
                    .rightPush(userKey, information);

            then(redisTemplate).should()
                    .expire(userKey, REFRESH_VALIDITY);

            then(valueOperations).should()
                    .set(
                            startsWith("jwt:access:"),
                            eq("active"),
                            eq(ACCESS_VALIDITY)
                    );

            then(valueOperations).should()
                    .set(
                            startsWith("jwt:refresh:"),
                            eq("active"),
                            eq(REFRESH_VALIDITY)
                    );

            then(redisLockProvider).should()
                    .releaseLock(userId.toString(), LOCK_VALUE);
        }

        @Test
        void registerJwtInformation_existingLogin_removesOldTokens() {
            // given
            JwtInformation oldInformation =
                    createJwtInformation("old-access", "old-refresh");

            JwtInformation newInformation =
                    createJwtInformation(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN);

            given(newInformation.getUserDto().id()).willReturn(userId);
            given(redisLockProvider.acquireLock(userId.toString()))
                    .willReturn(LOCK_VALUE);

            given(redisTemplate.opsForList()).willReturn(listOperations);
            given(redisTemplate.opsForValue()).willReturn(valueOperations);

            given(listOperations.size(userKey))
                    .willReturn(1L, 0L);

            given(listOperations.leftPop(userKey))
                    .willReturn(oldInformation);

            // when
            jwtRegistry.registerJwtInformation(newInformation);

            // then
            then(listOperations).should()
                    .leftPop(userKey);

            then(listOperations).should()
                    .rightPush(userKey, newInformation);

            // 기존 토큰 인덱스 삭제
            then(redisTemplate).should()
                    .delete(startsWith("jwt:access:"));

            then(redisTemplate).should()
                    .delete(startsWith("jwt:refresh:"));

            // 새 토큰 인덱스 등록
            then(valueOperations).should()
                    .set(
                            startsWith("jwt:access:"),
                            eq("active"),
                            eq(ACCESS_VALIDITY)
                    );

            then(valueOperations).should()
                    .set(
                            startsWith("jwt:refresh:"),
                            eq("active"),
                            eq(REFRESH_VALIDITY)
                    );

            then(redisLockProvider).should()
                    .releaseLock(userId.toString(), LOCK_VALUE);
        }
    }

    @Nested
    class InvalidateTest {

        @Test
        void invalidateJwtInformationByUserId_success() {
            // given
            JwtInformation information =
                    createJwtInformation(ACCESS_TOKEN, REFRESH_TOKEN);

            given(redisLockProvider.acquireLock(userId.toString()))
                    .willReturn(LOCK_VALUE);

            given(redisTemplate.opsForList()).willReturn(listOperations);
            given(listOperations.range(userKey, 0, -1))
                    .willReturn(List.of(information));

            // when
            jwtRegistry.invalidateJwtInformationByUserId(userId);

            // then
            then(redisTemplate).should()
                    .delete(startsWith("jwt:access:"));

            then(redisTemplate).should()
                    .delete(startsWith("jwt:refresh:"));

            then(redisTemplate).should()
                    .delete(userKey);

            then(redisLockProvider).should()
                    .releaseLock(userId.toString(), LOCK_VALUE);
        }

        @Test
        void invalidateJwtInformationByRefreshToken_success() {
            // given
            JwtInformation information =
                    createJwtInformation(ACCESS_TOKEN, REFRESH_TOKEN);

            given(redisTemplate.keys("jwt:user:*"))
                    .willReturn(Set.of(userKey));

            given(redisTemplate.opsForList()).willReturn(listOperations);

            // 락 획득 전 조회 + 락 획득 후 재조회
            given(listOperations.range(userKey, 0, -1))
                    .willReturn(List.of(information));

            given(redisLockProvider.acquireLock(userId.toString()))
                    .willReturn(LOCK_VALUE);

            given(listOperations.size(userKey)).willReturn(0L);

            // when
            jwtRegistry.invalidateJwtInformationByRefreshToken(REFRESH_TOKEN);

            // then
            then(listOperations).should(times(2))
                    .range(userKey, 0, -1);

            then(listOperations).should()
                    .remove(userKey, 1, information);

            then(redisTemplate).should()
                    .delete(startsWith("jwt:access:"));

            then(redisTemplate).should()
                    .delete(startsWith("jwt:refresh:"));

            then(redisTemplate).should()
                    .delete(userKey);

            then(redisLockProvider).should()
                    .releaseLock(userId.toString(), LOCK_VALUE);
        }

        @Test
        void invalidateJwtInformationByRefreshToken_notFound() {
            // given
            given(redisTemplate.keys("jwt:user:*"))
                    .willReturn(Set.of());

            // when
            jwtRegistry.invalidateJwtInformationByRefreshToken(REFRESH_TOKEN);

            // then
            then(redisTemplate).should(never())
                    .delete(anyString());

            then(redisLockProvider).shouldHaveNoInteractions();
        }
    }

    @Nested
    class ActiveTokenTest {

        @Test
        void hasActiveJwtInformationByUserId_success() {
            // given
            given(redisTemplate.opsForList()).willReturn(listOperations);
            given(listOperations.size(userKey)).willReturn(1L);

            // when
            boolean result =
                    jwtRegistry.hasActiveJwtInformationByUserId(userId);

            // then
            assertThat(result).isTrue();
        }

        @Test
        void hasActiveJwtInformationByUserId_notFound() {
            // given
            given(redisTemplate.opsForList()).willReturn(listOperations);
            given(listOperations.size(userKey)).willReturn(0L);

            // when
            boolean result =
                    jwtRegistry.hasActiveJwtInformationByUserId(userId);

            // then
            assertThat(result).isFalse();
        }

        @Test
        void hasActiveJwtInformationByAccessToken_success() {
            // given
            given(redisTemplate.hasKey(startsWith("jwt:access:")))
                    .willReturn(true);

            // when
            boolean result =
                    jwtRegistry.hasActiveJwtInformationByAccessToken(
                            ACCESS_TOKEN
                    );

            // then
            assertThat(result).isTrue();
        }

        @Test
        void hasActiveJwtInformationByRefreshToken_success() {
            // given
            given(redisTemplate.hasKey(startsWith("jwt:refresh:")))
                    .willReturn(true);

            // when
            boolean result =
                    jwtRegistry.hasActiveJwtInformationByRefreshToken(
                            REFRESH_TOKEN
                    );

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    class RotateTest {

        @Test
        void rotateJwtInformation_success() {
            // given
            JwtInformation oldInformation =
                    createJwtInformation(ACCESS_TOKEN, REFRESH_TOKEN);

            JwtInformation newInformation =
                    createJwtInformation(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN);

            given(newInformation.getUserDto().id()).willReturn(userId);
            given(redisLockProvider.acquireLock(userId.toString()))
                    .willReturn(LOCK_VALUE);

            given(redisTemplate.opsForList()).willReturn(listOperations);
            given(redisTemplate.opsForValue()).willReturn(valueOperations);

            given(listOperations.range(userKey, 0, -1))
                    .willReturn(List.of(oldInformation));

            // when
            jwtRegistry.rotateJwtInformation(
                    REFRESH_TOKEN,
                    newInformation
            );

            // then
            assertThat(oldInformation.getAccessToken())
                    .isEqualTo(NEW_ACCESS_TOKEN);

            assertThat(oldInformation.getRefreshToken())
                    .isEqualTo(NEW_REFRESH_TOKEN);

            then(listOperations).should()
                    .set(userKey, 0, oldInformation);

            // 기존 인덱스 삭제
            then(redisTemplate).should()
                    .delete(startsWith("jwt:access:"));

            then(redisTemplate).should()
                    .delete(startsWith("jwt:refresh:"));

            // 새로운 인덱스 등록
            then(valueOperations).should()
                    .set(
                            startsWith("jwt:access:"),
                            eq("active"),
                            eq(ACCESS_VALIDITY)
                    );

            then(valueOperations).should()
                    .set(
                            startsWith("jwt:refresh:"),
                            eq("active"),
                            eq(REFRESH_VALIDITY)
                    );

            then(redisTemplate).should()
                    .expire(userKey, REFRESH_VALIDITY);

            then(redisLockProvider).should()
                    .releaseLock(userId.toString(), LOCK_VALUE);
        }

        @Test
        void rotateJwtInformation_invalidRefreshToken_throwsException() {
            // given
            JwtInformation newInformation =
                    createJwtInformation(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN);

            given(newInformation.getUserDto().id()).willReturn(userId);
            given(redisLockProvider.acquireLock(userId.toString()))
                    .willReturn(LOCK_VALUE);

            given(redisTemplate.opsForList()).willReturn(listOperations);
            given(listOperations.range(userKey, 0, -1))
                    .willReturn(List.of());

            // when & then
            assertThatThrownBy(
                    () -> jwtRegistry.rotateJwtInformation(
                            REFRESH_TOKEN,
                            newInformation
                    )
            ).isInstanceOf(InvalidJwtException.class);

            then(redisLockProvider).should()
                    .releaseLock(userId.toString(), LOCK_VALUE);

            then(redisTemplate).should(never())
                    .delete(anyString());
        }
    }

    @Nested
    class CleanupTest {

        @Test
        void clearExpiredJwtInformation_removesExpiredTokens() {
            // given
            JwtInformation expiredInformation =
                    createJwtInformation(ACCESS_TOKEN, REFRESH_TOKEN);

            given(redisTemplate.keys("jwt:user:*"))
                    .willReturn(Set.of(userKey));

            given(redisTemplate.opsForList()).willReturn(listOperations);

            given(listOperations.range(userKey, 0, -1))
                    .willReturn(List.of(expiredInformation));

            given(jwtTokenProvider.isValid(REFRESH_TOKEN))
                    .willReturn(false);

            given(listOperations.size(userKey)).willReturn(0L);

            // when
            jwtRegistry.clearExpiredJwtInformation();

            // then
            then(listOperations).should()
                    .remove(userKey, 1, expiredInformation);

            then(redisTemplate).should()
                    .delete(startsWith("jwt:access:"));

            then(redisTemplate).should()
                    .delete(startsWith("jwt:refresh:"));

            then(redisTemplate).should()
                    .delete(userKey);
        }
    }
}
