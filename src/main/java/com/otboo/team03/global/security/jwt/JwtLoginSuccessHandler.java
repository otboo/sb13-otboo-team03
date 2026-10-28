
package com.otboo.team03.global.security.jwt;

import com.otboo.team03.global.dto.JwtDto;
import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.global.config.JwtProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtRegistry jwtRegistry;
    private final ObjectMapper objectMapper;
    private final JwtProperties jwtProperties;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OtbooUserDetails userDetails = (OtbooUserDetails) authentication.getPrincipal();

        UUID userId = userDetails.getUserDto().id();

        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);

        JwtInformation jwtInformation = new JwtInformation(
                userDetails.getUserDto(),
                accessToken,
                refreshToken
        );

        jwtRegistry.registerJwtInformation(jwtInformation);

        ResponseCookie refreshCookie = refreshTokenCookieProvider.create(
                refreshToken,
                jwtProperties.refreshTokenValidity()
        );

        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        JwtDto jwtDto = new JwtDto(userDetails.getUserDto(), accessToken);

        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(response.getWriter(), jwtDto);
    }
}
