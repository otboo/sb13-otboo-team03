
package com.otboo.team03.global.security.jwt;

import com.nimbusds.jwt.JWTClaimsSet;
import com.otboo.team03.global.config.JwtProperties;
import com.otboo.team03.global.exception.jwt.InvalidJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtTokenProviderTest {

    private static final String SECRET =
            "otboo-test-secret-key-12345678901234567890";

    private static final Duration ACCESS_VALIDITY = Duration.ofMinutes(30);
    private static final Duration REFRESH_VALIDITY = Duration.ofDays(7);

    private JwtTokenProvider jwtTokenProvider;
    private UUID userId;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                SECRET,
                ACCESS_VALIDITY,
                REFRESH_VALIDITY
        );

        jwtTokenProvider = new JwtTokenProvider(properties);
        userId = UUID.randomUUID();
    }

    @Nested
    class AccessTokenTest {

        @Test
        void createAccessToken_success() throws ParseException {
            // when
            String token = jwtTokenProvider.createAccessToken(userId);

            JWTClaimsSet claims = jwtTokenProvider.parseClaims(token);

            // then
            assertThat(token).isNotBlank();
            assertThat(claims.getSubject()).isEqualTo(userId.toString());
            assertThat(claims.getStringClaim(JwtTokenProvider.CLAIM_TYPE))
                    .isEqualTo("ACCESS");
            assertThat(claims.getJWTID()).isNotBlank();
            assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(userId);
            assertThat(jwtTokenProvider.isValid(token)).isTrue();

            assertThatCode(() -> jwtTokenProvider.validateAccessToken(token))
                    .doesNotThrowAnyException();
        }

        @Test
        void createAccessToken_generatesDifferentTokens() {
            // when
            String firstToken = jwtTokenProvider.createAccessToken(userId);
            String secondToken = jwtTokenProvider.createAccessToken(userId);

            // then
            assertThat(firstToken).isNotEqualTo(secondToken);
        }

        @Test
        void validateAccessToken_withRefreshToken_throwsException() {
            // given
            String refreshToken = jwtTokenProvider.createRefreshToken(userId);

            // when & then
            assertThatThrownBy(
                    () -> jwtTokenProvider.validateAccessToken(refreshToken)
            ).isInstanceOf(InvalidJwtException.class);
        }
    }

    @Nested
    class RefreshTokenTest {

        @Test
        void createRefreshToken_success() throws ParseException {
            // when
            String token = jwtTokenProvider.createRefreshToken(userId);

            JWTClaimsSet claims = jwtTokenProvider.parseClaims(token);

            // then
            assertThat(token).isNotBlank();
            assertThat(claims.getSubject()).isEqualTo(userId.toString());
            assertThat(claims.getStringClaim(JwtTokenProvider.CLAIM_TYPE))
                    .isEqualTo("REFRESH");
            assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(userId);
            assertThat(jwtTokenProvider.isValid(token)).isTrue();

            assertThatCode(() -> jwtTokenProvider.validateRefreshToken(token))
                    .doesNotThrowAnyException();
        }

        @Test
        void validateRefreshToken_withAccessToken_throwsException() {
            // given
            String accessToken = jwtTokenProvider.createAccessToken(userId);

            // when & then
            assertThatThrownBy(
                    () -> jwtTokenProvider.validateRefreshToken(accessToken)
            ).isInstanceOf(InvalidJwtException.class);
        }
    }

    @Nested
    class ValidationTest {

        @Test
        void parseClaims_withExpiredToken_throwsException() throws Exception {
            // given
            JwtTokenProvider expiredProvider = new JwtTokenProvider(
                    new JwtProperties(
                            SECRET,
                            Duration.ofSeconds(-10),
                            REFRESH_VALIDITY
                    )
            );

            String expiredToken = expiredProvider.createAccessToken(userId);

            // when & then
            assertThatThrownBy(
                    () -> jwtTokenProvider.parseClaims(expiredToken)
            ).isInstanceOf(InvalidJwtException.class);

            assertThat(jwtTokenProvider.isValid(expiredToken)).isFalse();
        }

        @Test
        void parseClaims_withDifferentSecret_throwsException() {
            // given
            JwtTokenProvider anotherProvider = new JwtTokenProvider(
                    new JwtProperties(
                            "different-test-secret-key-1234567890123456",
                            ACCESS_VALIDITY,
                            REFRESH_VALIDITY
                    )
            );

            String token = anotherProvider.createAccessToken(userId);

            // when & then
            assertThatThrownBy(
                    () -> jwtTokenProvider.parseClaims(token)
            ).isInstanceOf(InvalidJwtException.class);

            assertThat(jwtTokenProvider.isValid(token)).isFalse();
        }

        @Test
        void parseClaims_withTamperedToken_throwsException() {
            // given
            String token = jwtTokenProvider.createAccessToken(userId);

            String[] parts = token.split("\\.");

            String payload = new String(
                    Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8
            );

            String tamperedPayload = payload.replace(
                    userId.toString(),
                    UUID.randomUUID().toString()
            );

            String tamperedToken = parts[0] + "."
                    + Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                            tamperedPayload.getBytes(StandardCharsets.UTF_8)
                    )
                    + "." + parts[2];

            // when & then
            assertThatThrownBy(
                    () -> jwtTokenProvider.parseClaims(tamperedToken)
            ).isInstanceOf(InvalidJwtException.class);
        }

        @Test
        void parseClaims_withInvalidToken_throwsException() {
            // when & then
            assertThatThrownBy(
                    () -> jwtTokenProvider.parseClaims("invalid.jwt.token")
            ).isInstanceOf(InvalidJwtException.class);

            assertThat(jwtTokenProvider.isValid("invalid.jwt.token"))
                    .isFalse();
        }

        @Test
        void parseClaims_withNullToken_throwsException() {
            assertThatThrownBy(
                    () -> jwtTokenProvider.parseClaims(null)
            ).isInstanceOf(InvalidJwtException.class);
        }

        @Test
        void parseClaims_withBlankToken_throwsException() {
            assertThatThrownBy(
                    () -> jwtTokenProvider.parseClaims(" ")
            ).isInstanceOf(InvalidJwtException.class);
        }
    }

    @Nested
    class SecretTest {

        @Test
        void constructor_withShortSecret_throwsException() {
            // given
            JwtProperties properties = new JwtProperties(
                    "short-secret",
                    ACCESS_VALIDITY,
                    REFRESH_VALIDITY
            );

            // when & then
            assertThatThrownBy(
                    () -> new JwtTokenProvider(properties)
            ).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("최소 32바이트");
        }
    }
}
