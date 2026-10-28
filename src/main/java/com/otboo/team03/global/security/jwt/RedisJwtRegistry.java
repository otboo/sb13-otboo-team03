
package com.otboo.team03.global.security.jwt;

import com.otboo.team03.global.exception.jwt.InvalidJwtException;
import com.otboo.team03.global.redis.RedisLockProvider;
import com.otboo.team03.global.redis.RedisLockProvider.RedisLockAcquisitionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisJwtRegistry implements JwtRegistry {

    private static final String USER_JWT_KEY_PREFIX = "jwt:user:";
    private static final String ACCESS_TOKEN_INDEX_PREFIX = "jwt:access:";
    private static final String REFRESH_TOKEN_INDEX_PREFIX = "jwt:refresh:";

    @Value("${otboo.jwt.access-token-validity}")
    private Duration accessTokenValidity;
    @Value("${otboo.jwt.refresh-token-validity}")
    private Duration refreshTokenValidity;

    private final int maxActiveJwtCount = 1;

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisLockProvider redisLockProvider;

    // JWT 등록: 기존 로그인 정보 제거 후 신규 등록
    @Retryable(
            retryFor = RedisLockAcquisitionException.class,
            maxAttempts = 10,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Override
    public void registerJwtInformation(JwtInformation jwtInformation) {
        UUID userId = jwtInformation.getUserDto().id();
        String userKey = getUserKey(userId);
        String lockKey = userId.toString();

        String lockValue = redisLockProvider.acquireLock(lockKey);

        try {
            Long currentSize = redisTemplate.opsForList().size(userKey);

            while (currentSize != null && currentSize >= maxActiveJwtCount) {
                Object oldestTokenObj = redisTemplate.opsForList().leftPop(userKey);

                if (oldestTokenObj instanceof JwtInformation oldestToken) {
                    removeTokenIndex(
                            oldestToken.getAccessToken(),
                            oldestToken.getRefreshToken()
                    );
                }

                currentSize = redisTemplate.opsForList().size(userKey);
            }

            redisTemplate.opsForList().rightPush(userKey, jwtInformation);
            redisTemplate.expire(userKey, refreshTokenValidity);

            addTokenIndex(
                    jwtInformation.getAccessToken(),
                    jwtInformation.getRefreshToken()
            );

        } finally {
            redisLockProvider.releaseLock(lockKey, lockValue);
        }
    }

    // 사용자 ID 기준 JWT 무효화
    @Retryable(
            retryFor = RedisLockAcquisitionException.class,
            maxAttempts = 10,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Override
    public void invalidateJwtInformationByUserId(UUID userId) {
        String userKey = getUserKey(userId);
        String lockKey = userId.toString();

        String lockValue = redisLockProvider.acquireLock(lockKey);

        try {
            List<Object> tokens = redisTemplate.opsForList().range(userKey, 0, -1);

            if (tokens != null) {
                for (Object tokenObj : tokens) {
                    if (tokenObj instanceof JwtInformation jwtInfo) {
                        removeTokenIndex(
                                jwtInfo.getAccessToken(),
                                jwtInfo.getRefreshToken()
                        );
                    }
                }
            }

            redisTemplate.delete(userKey);

        } finally {
            redisLockProvider.releaseLock(lockKey, lockValue);
        }
    }

    // Refresh Token 기준 JWT 무효화
    @Retryable(
            retryFor = RedisLockAcquisitionException.class,
            maxAttempts = 10,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Override
    public void invalidateJwtInformationByRefreshToken(String refreshToken) {
        Set<String> userKeys = redisTemplate.keys(USER_JWT_KEY_PREFIX + "*");

        if (userKeys == null || userKeys.isEmpty()) {
            return;
        }

        for (String userKey : userKeys) {
            List<Object> tokens = redisTemplate.opsForList().range(userKey, 0, -1);

            if (tokens == null) {
                continue;
            }

            boolean containsToken = tokens.stream()
                    .filter(JwtInformation.class::isInstance)
                    .map(JwtInformation.class::cast)
                    .anyMatch(jwtInfo -> jwtInfo.getRefreshToken().equals(refreshToken));

            if (!containsToken) {
                continue;
            }

            String lockKey = userKey.substring(USER_JWT_KEY_PREFIX.length());
            String lockValue = redisLockProvider.acquireLock(lockKey);

            try {
                // 락 획득 후 다시 조회
                List<Object> currentTokens = redisTemplate.opsForList()
                        .range(userKey, 0, -1);

                if (currentTokens == null) {
                    return;
                }

                for (Object tokenObj : currentTokens) {
                    if (tokenObj instanceof JwtInformation jwtInfo
                            && jwtInfo.getRefreshToken().equals(refreshToken)) {

                        redisTemplate.opsForList().remove(userKey, 1, tokenObj);

                        removeTokenIndex(
                                jwtInfo.getAccessToken(),
                                jwtInfo.getRefreshToken()
                        );

                        Long remaining = redisTemplate.opsForList().size(userKey);

                        if (remaining == null || remaining == 0) {
                            redisTemplate.delete(userKey);
                        }

                        return;
                    }
                }

                return;

            } finally {
                redisLockProvider.releaseLock(lockKey, lockValue);
            }
        }
    }

    // 사용자 로그인 상태 확인
    @Override
    public boolean hasActiveJwtInformationByUserId(UUID userId) {
        String userKey = getUserKey(userId);
        Long size = redisTemplate.opsForList().size(userKey);

        return size != null && size > 0;
    }

    // Access Token 활성 여부 확인
    @Override
    public boolean hasActiveJwtInformationByAccessToken(String accessToken) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(
                        getTokenKey(ACCESS_TOKEN_INDEX_PREFIX, accessToken)
                )
        );
    }

    // Refresh Token 활성 여부 확인
    @Override
    public boolean hasActiveJwtInformationByRefreshToken(String refreshToken) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(
                        getTokenKey(REFRESH_TOKEN_INDEX_PREFIX, refreshToken)
                )
        );
    }

    // JWT 재발급 시 기존 토큰 교체
    @Override
    @Retryable(
            retryFor = RedisLockAcquisitionException.class,
            maxAttempts = 10,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public void rotateJwtInformation(String refreshToken, JwtInformation newJwtInformation) {
        UUID userId = newJwtInformation.getUserDto().id();
        String userKey = getUserKey(userId);
        String lockKey = userId.toString();

        String lockValue = redisLockProvider.acquireLock(lockKey);

        try {
            List<Object> tokens = redisTemplate.opsForList().range(userKey, 0, -1);

            if (tokens != null) {
                for (int i = 0; i < tokens.size(); i++) {
                    if (tokens.get(i) instanceof JwtInformation jwtInfo && jwtInfo.getRefreshToken().equals(refreshToken)) {

                        removeTokenIndex(
                                jwtInfo.getAccessToken(),
                                jwtInfo.getRefreshToken()
                        );

                        jwtInfo.rotate(
                                newJwtInformation.getAccessToken(),
                                newJwtInformation.getRefreshToken()
                        );

                        redisTemplate.opsForList().set(userKey, i, jwtInfo);

                        addTokenIndex(
                                newJwtInformation.getAccessToken(),
                                newJwtInformation.getRefreshToken()
                        );

                        redisTemplate.expire(userKey, refreshTokenValidity);
                        return;
                    }
                }
            }

            // 기존 Refresh Token이 없으면 재발급 실패
            throw new InvalidJwtException();

        } finally {
            redisLockProvider.releaseLock(lockKey, lockValue);
        }
    }

    // 만료된 JWT 정리
    @Scheduled(fixedDelayString = "${scheduler.jwt-cleanup.fixed-delay:PT5M}")
    @Override
    public void clearExpiredJwtInformation() {
        Set<String> userKeys = redisTemplate.keys(USER_JWT_KEY_PREFIX + "*");

        if (userKeys == null || userKeys.isEmpty()) {
            return;
        }

        for (String userKey : userKeys) {
            List<Object> tokens = redisTemplate.opsForList().range(userKey, 0, -1);

            if (tokens == null) {
                continue;
            }

            for (Object tokenObj : tokens) {
                if (tokenObj instanceof JwtInformation jwtInfo
                        && !jwtTokenProvider.isValid(jwtInfo.getRefreshToken())) {

                    redisTemplate.opsForList().remove(userKey, 1, tokenObj);

                    removeTokenIndex(
                            jwtInfo.getAccessToken(),
                            jwtInfo.getRefreshToken()
                    );
                }
            }

            Long remaining = redisTemplate.opsForList().size(userKey);

            if (remaining == null || remaining == 0) {
                redisTemplate.delete(userKey);
            }
        }
    }

    // 사용자별 Redis 키 생성
    private String getUserKey(UUID userId) {
        return USER_JWT_KEY_PREFIX + userId;
    }

    // Access/Refresh Token 인덱스 등록
    private void addTokenIndex(String accessToken, String refreshToken) {
        redisTemplate.opsForValue().set(
                getTokenKey(ACCESS_TOKEN_INDEX_PREFIX, accessToken),
                "active",
                accessTokenValidity
        );

        redisTemplate.opsForValue().set(
                getTokenKey(REFRESH_TOKEN_INDEX_PREFIX, refreshToken),
                "active",
                refreshTokenValidity
        );
    }

    // Access/Refresh Token 인덱스 제거
    private void removeTokenIndex(String accessToken, String refreshToken) {
        redisTemplate.delete(getTokenKey(ACCESS_TOKEN_INDEX_PREFIX, accessToken));

        redisTemplate.delete(getTokenKey(REFRESH_TOKEN_INDEX_PREFIX, refreshToken));
    }

    // 토큰 Key 생성 메서드
    private String getTokenKey(String prefix, String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));

            return prefix + HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
