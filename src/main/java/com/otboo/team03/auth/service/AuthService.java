
package com.otboo.team03.auth.service;

import com.otboo.team03.global.dto.JwtDto;
import com.otboo.team03.global.dto.JwtRefreshResult;
import com.otboo.team03.global.exception.jwt.InvalidJwtException;
import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.global.security.OtbooUserDetailsService;
import com.otboo.team03.global.security.jwt.JwtInformation;
import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtRegistry jwtRegistry;
    private final OtbooUserDetailsService userDetailsService;

    public JwtRefreshResult refresh(String refreshToken) {

        // Refresh Token 서명, 만료, 유형 검증
        jwtTokenProvider.validateRefreshToken(refreshToken);

        // Redis에 활성화된 Refresh Token인지 확인
        if (!jwtRegistry.hasActiveJwtInformationByRefreshToken(refreshToken)) {
            throw new InvalidJwtException();
        }

        // 토큰에서 사용자 UUID 추출
        UUID userId = jwtTokenProvider.getUserId(refreshToken);

        OtbooUserDetails userDetails = (OtbooUserDetails) userDetailsService.loadUserById(userId);

        // 계정 잠금 및 비활성 상태 확인
        if (!userDetails.isEnabled()
                || !userDetails.isAccountNonLocked()
                || !userDetails.isAccountNonExpired()
                || !userDetails.isCredentialsNonExpired()) {
            throw new InvalidJwtException();
        }

        // 새 Access / Refresh Token 발급
        String newAccessToken = jwtTokenProvider.createAccessToken(userId);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);

        JwtInformation newJwtInformation = new JwtInformation(
                userDetails.getUserDto(),
                newAccessToken,
                newRefreshToken
        );

        // Redis에 저장된 기존 JWT 교체
        jwtRegistry.rotateJwtInformation(refreshToken, newJwtInformation);

        JwtDto jwtDto = new JwtDto(userDetails.getUserDto(), newAccessToken);

        return new JwtRefreshResult(jwtDto, newRefreshToken);
    }
}
