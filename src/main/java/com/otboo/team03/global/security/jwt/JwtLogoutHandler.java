
package com.otboo.team03.global.security.jwt;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtLogoutHandler implements LogoutHandler {

    private final JwtRegistry jwtRegistry;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @Override
    public void logout(HttpServletRequest request,
                       HttpServletResponse response,
                       Authentication authentication) {

        Arrays.stream(Optional.ofNullable(request.getCookies())
                        .orElse(new Cookie[0]))
                .filter(cookie -> "REFRESH_TOKEN".equals(cookie.getName()))
                .findFirst()
                .ifPresent(cookie ->
                        jwtRegistry.invalidateJwtInformationByRefreshToken(cookie.getValue())
                );

        // Refresh Token 쿠키는 항상 삭제
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.delete().toString());
    }
}
