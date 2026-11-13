package com.otboo.team03.auth.service;

import com.otboo.team03.auth.dto.SocialIdentity;
import com.otboo.team03.auth.entity.SocialProvider;
import com.otboo.team03.global.config.JwtProperties;
import com.otboo.team03.global.security.jwt.JwtInformation;
import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.global.security.jwt.JwtTokenProvider;
import com.otboo.team03.global.security.jwt.RefreshTokenCookieProvider;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SocialLoginHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private static final String SUCCESS_URL = "/#/recommendations";
    private static final String FAILURE_URL = "/#/auth/login?error=oauth_failed&error_message=";

    private final SocialAccountService socialAccountService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtRegistry jwtRegistry;
    private final JwtProperties jwtProperties;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    // 소셜 회원 정보로 OTBOO 계정을 찾거나 생성한다.
    // JWT를 등록하고 Refresh Token 쿠키를 내려준 뒤 피드로 이동한다.
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        User user;
        try {
            SocialIdentity identity = extractIdentity(authentication);
            user = socialAccountService.resolveUser(identity);
        } catch (AuthenticationException e) {
            onAuthenticationFailure(request, response, e);
            return;
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        jwtRegistry.registerJwtInformation(new JwtInformation(UserDto.from(user), accessToken, refreshToken));

        ResponseCookie refreshCookie = refreshTokenCookieProvider.create(
                refreshToken,
                jwtProperties.refreshTokenValidity()
        );
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        // URL에 토큰을 넣지 않고, 이동한 화면에서 기존 refresh API로 로그인 상태를 복원한다.
        response.sendRedirect(SUCCESS_URL);
    }

    // 인증 정보를 지우고, 내부 오류 메시지 대신 정해진 오류 코드로 로그인 화면에 돌아간다.
    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {

        SecurityContextHolder.clearContext();

        // 외부 제공자의 오류 설명이나 내부 예외 메시지는 URL에 넣지 않는다.
        String errorMessage = "login_failed";
        if (exception instanceof OAuth2AuthenticationException oauthException
                && "access_denied".equals(oauthException.getError().getErrorCode())) {
            errorMessage = "access_denied";
        }

        response.sendRedirect(FAILURE_URL + errorMessage);
    }

    // OAuth2 클라이언트 등록 ID로 제공자를 구분하고, 회원 정보를 공통 형식으로 변환한다.
    private SocialIdentity extractIdentity(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            throw new BadCredentialsException("Invalid OAuth2 authentication");
        }

        return
                switch (token.getAuthorizedClientRegistrationId()) {
                    case "google" -> extractGoogleIdentity(token.getPrincipal());
                    case "kakao" -> extractKakaoIdentity(token.getPrincipal());
                    default -> throw new BadCredentialsException("Unsupported social provider");
                };
    }

    // 검증된 Google ID 토큰에서 회원 ID, 이름, 이메일과 이메일 검증 여부를 읽는다.
    private SocialIdentity extractGoogleIdentity(OAuth2User principal) {
        if (!(principal instanceof OidcUser oidcUser)) {
            throw new BadCredentialsException("Invalid Google OIDC principal");
        }

        // Google 회원 ID와 이메일 검증 여부는 검증된 ID 토큰에서 읽는다.
        Map<String, Object> claims = oidcUser.getIdToken().getClaims();
        return new SocialIdentity(
                SocialProvider.GOOGLE,
                getString(claims.get("sub")),
                getString(claims.get("email")),
                getString(claims.get("name")),
                Boolean.TRUE.equals(claims.get("email_verified"))
        );
    }

    // 카카오 응답에서 회원 ID와 닉네임만 읽고, 가상 이메일 생성은 계정 처리 서비스에 맡긴다.
    private SocialIdentity extractKakaoIdentity(OAuth2User principal) {
        Object id = principal.getAttribute("id");
        if (id == null) {
            throw new BadCredentialsException("Missing Kakao member ID");
        }

        String nickname = null;
        if (principal.getAttribute("kakao_account") instanceof Map<?, ?> account
                && account.get("profile") instanceof Map<?, ?> profile) {
            nickname = getString(profile.get("nickname"));
        }

        return new SocialIdentity(SocialProvider.KAKAO, id.toString(), null, nickname, false);
    }

    private String getString(Object value) {
        return value instanceof String text ? text : null;
    }
}