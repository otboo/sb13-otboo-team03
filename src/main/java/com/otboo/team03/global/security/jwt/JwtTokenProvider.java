
package com.otboo.team03.global.security.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.otboo.team03.global.config.JwtProperties;
import com.otboo.team03.global.exception.jwt.InvalidJwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
@Slf4j
public class JwtTokenProvider {

    public static final String CLAIM_TYPE = "type";

    private static final String ACCESS = "ACCESS";
    private static final String REFRESH = "REFRESH";

    private final String secret;
    private final Duration accessTokenValidity;
    private final Duration refreshTokenValidity;

    public JwtTokenProvider(JwtProperties properties) {
        this.secret = properties.secret();
        this.accessTokenValidity = properties.accessTokenValidity();
        this.refreshTokenValidity = properties.refreshTokenValidity();

        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "JWT Secret은 최소 32바이트 이상이어야 합니다."
            );
        }
    }

    public String createAccessToken(UUID userId) {
        return createToken(userId, ACCESS, accessTokenValidity);
    }

    public String createRefreshToken(UUID userId) {
        return createToken(userId, REFRESH, refreshTokenValidity);
    }

    private String createToken(UUID userId, String type, Duration validity) {
        try {
            Instant now = Instant.now();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .claim(CLAIM_TYPE, type)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(validity)))
                    .jwtID(UUID.randomUUID().toString())
                    .build();

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claims
            );

            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));

            return jwt.serialize();

        } catch (JOSEException e) {
            throw new InvalidJwtException();
        }
    }

    // JWT 서명 및 만료 검증 후 Claims 반환
    public JWTClaimsSet parseClaims(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidJwtException();
        }

        try {
            var processor = new DefaultJWTProcessor<SecurityContext>();

            processor.setJWSKeySelector(
                    new JWSVerificationKeySelector<>(
                            JWSAlgorithm.HS256,
                            new ImmutableSecret<>(
                                    secret.getBytes(StandardCharsets.UTF_8)
                            )
                    )
            );

            JWTClaimsSet claims = processor.process(token, null);

            UUID.fromString(claims.getSubject());
            UUID.fromString(claims.getJWTID());

            if (claims.getIssueTime() == null
                    || claims.getExpirationTime() == null
                    || !claims.getExpirationTime().toInstant()
                    .isAfter(Instant.now())) {
                throw new InvalidJwtException();
            }

            String type = claims.getStringClaim(CLAIM_TYPE);

            if (!ACCESS.equals(type) && !REFRESH.equals(type)) {
                throw new InvalidJwtException();
            }

            return claims;

        } catch (ParseException | BadJOSEException | JOSEException
                 | IllegalArgumentException e) {
            throw new InvalidJwtException();
        }
    }

    // Access Token 검증
    public void validateAccessToken(String token) {
        validateTokenType(token, ACCESS);
    }

    // Refresh Token 검증
    public void validateRefreshToken(String token) {
        validateTokenType(token, REFRESH);
    }

    private void validateTokenType(String token, String expectedType) {
        JWTClaimsSet claims = parseClaims(token);

        try {
            if (!expectedType.equals(claims.getStringClaim(CLAIM_TYPE))) {
                throw new InvalidJwtException();
            }
        } catch (ParseException e) {
            throw new InvalidJwtException();
        }
    }

    // JWT 유효 여부 확인
    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (InvalidJwtException e) {
            log.warn("[JWT] 유효하지 않은 토큰: {}", e.getMessage());
            return false;
        }
    }

    // 사용자 UUID 추출
    public UUID getUserId(String token) {
        return getUserId(parseClaims(token));
    }

    public UUID getUserId(JWTClaimsSet claims) {
        try {
            return UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException e) {
            throw new InvalidJwtException();
        }
    }
}
