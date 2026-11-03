package com.otboo.team03.auth.controller;

import com.otboo.team03.auth.service.AuthService;
import com.otboo.team03.global.config.JwtProperties;
import com.otboo.team03.global.dto.JwtDto;
import com.otboo.team03.global.dto.JwtRefreshResult;
import com.otboo.team03.global.security.jwt.RefreshTokenCookieProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;
    private final JwtProperties jwtProperties;

    // CSRF 토큰 발급
    @GetMapping("/csrf-token")
    public ResponseEntity<Void> getCsrfToken(CsrfToken csrfToken) {
        csrfToken.getToken();

        return ResponseEntity.noContent().build();
    }

    // JWT 재발급
    @PostMapping("/refresh")
    public ResponseEntity<JwtDto> refresh(
            @CookieValue(name = "REFRESH_TOKEN", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        JwtRefreshResult result = authService.refresh(refreshToken);

        ResponseCookie refreshCookie = refreshTokenCookieProvider.create(
                result.refreshToken(),
                jwtProperties.refreshTokenValidity()
        );

        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ResponseEntity.ok(result.jwtDto());
    }
}

