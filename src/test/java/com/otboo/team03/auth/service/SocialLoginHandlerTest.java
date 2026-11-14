package com.otboo.team03.auth.service;

import com.otboo.team03.auth.dto.SocialIdentity;
import com.otboo.team03.auth.entity.SocialProvider;
import com.otboo.team03.global.config.JwtProperties;
import com.otboo.team03.global.security.jwt.JwtInformation;
import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.global.security.jwt.JwtTokenProvider;
import com.otboo.team03.global.security.jwt.RefreshTokenCookieProvider;
import com.otboo.team03.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SocialLoginHandlerTest {

    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String REFRESH_TOKEN = "test-refresh-token";
    private static final String LOGIN_FAILURE_URL = "/#/auth/login?error=oauth_failed&error_message=";

    @Mock
    private SocialAccountService socialAccountService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtRegistry jwtRegistry;

    private SocialLoginHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties("test-secret", Duration.ofMinutes(15), Duration.ofDays(7));
        handler = new SocialLoginHandler(
                socialAccountService, jwtTokenProvider, jwtRegistry, jwtProperties, new RefreshTokenCookieProvider()
        );
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Nested
    @DisplayName("OAuth 로그인 성공")
    class SuccessTest {

        @Test
        @DisplayName("Google 회원 정보를 전달하고 JWT를 등록한 뒤 피드로 이동한다")
        void onAuthenticationSuccess_google_redirectsToFeed() throws Exception {
            // given
            SocialIdentity identity = new SocialIdentity(
                    SocialProvider.GOOGLE, "google-user-123", "user@example.com", "구글 사용자", true
            );
            User user = stubLogin(identity, identity.email());

            // when
            handler.onAuthenticationSuccess(request, response, googleAuthentication());

            // then
            then(socialAccountService).should().resolveUser(identity);
            assertLoginResponse(user);
        }

        @Test
        @DisplayName("Kakao는 회원 ID와 닉네임을 전달하고 이메일은 전달하지 않는다")
        void onAuthenticationSuccess_kakao_resolvesUserWithoutEmail() throws Exception {
            // given
            SocialIdentity identity = new SocialIdentity(SocialProvider.KAKAO, "12345", null, "카카오닉네임", false);
            User user = stubLogin(identity, "카카오닉네임_12345@kakao.com");
            DefaultOAuth2User principal = new DefaultOAuth2User(
                    List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                    Map.of("id", 12345L, "kakao_account", Map.of("profile", Map.of("nickname", "카카오닉네임"))),
                    "id"
            );
            OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "kakao");

            // when
            handler.onAuthenticationSuccess(request, response, authentication);

            // then
            then(socialAccountService).should().resolveUser(identity);
            assertLoginResponse(user);
        }
    }

    @Nested
    @DisplayName("OAuth 로그인 실패")
    class FailureTest {

        @Test
        @DisplayName("인증을 취소하면 오류 파라미터와 함께 로그인 화면으로 이동한다")
        void onAuthenticationFailure_accessDenied_redirectsToLogin() throws Exception {
            // given
            OAuth2AuthenticationException exception = new OAuth2AuthenticationException(new OAuth2Error("access_denied"));

            // when
            handler.onAuthenticationFailure(request, response, exception);

            // then
            assertThat(response.getStatus()).isEqualTo(302);
            assertThat(response.getRedirectedUrl()).isEqualTo(LOGIN_FAILURE_URL + "access_denied");
            assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
            then(socialAccountService).shouldHaveNoInteractions();
            then(jwtTokenProvider).shouldHaveNoInteractions();
            then(jwtRegistry).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("계정 처리에 실패하면 토큰을 발급하지 않고 내부 오류 메시지를 숨긴다")
        void onAuthenticationSuccess_accountRejected_redirectsToLogin() throws Exception {
            // given
            SocialIdentity identity = new SocialIdentity(
                    SocialProvider.GOOGLE, "google-user-123", "user@example.com", "구글 사용자", true
            );
            given(socialAccountService.resolveUser(identity)).willThrow(new BadCredentialsException("내부 오류 메시지"));

            // when
            handler.onAuthenticationSuccess(request, response, googleAuthentication());

            // then
            assertThat(response.getStatus()).isEqualTo(302);
            assertThat(response.getRedirectedUrl()).isEqualTo(LOGIN_FAILURE_URL + "login_failed");
            assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
            then(jwtTokenProvider).shouldHaveNoInteractions();
            then(jwtRegistry).shouldHaveNoInteractions();
        }

        @ParameterizedTest
        @ValueSource(strings = {"unsupported", "google", "kakao"})
        @DisplayName("지원하지 않는 제공자이거나 필수 회원 정보가 없으면 로그인 화면으로 돌아간다")
        void onAuthenticationSuccess_invalidOAuthIdentity_redirectsToLogin(String registrationId) throws Exception {
            // given
            DefaultOAuth2User principal = new DefaultOAuth2User(
                    List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                    Map.of("sub", "test-user"),
                    "sub"
            );
            OAuth2AuthenticationToken authentication =
                    new OAuth2AuthenticationToken(principal, principal.getAuthorities(), registrationId);

            // when
            handler.onAuthenticationSuccess(request, response, authentication);

            // then
            assertLoginRejected();
        }

        @Test
        @DisplayName("OAuth 인증 객체가 아니면 JWT를 발급하지 않고 로그인 화면으로 돌아간다")
        void onAuthenticationSuccess_nonOAuthAuthentication_redirectsToLogin() throws Exception {
            // given
            var authentication = UsernamePasswordAuthenticationToken.unauthenticated("test-user", null);

            // when
            handler.onAuthenticationSuccess(request, response, authentication);

            // then
            assertLoginRejected();
        }

        private void assertLoginRejected() {
            assertThat(response.getStatus()).isEqualTo(302);
            assertThat(response.getRedirectedUrl()).isEqualTo(LOGIN_FAILURE_URL + "login_failed");
            assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
            then(socialAccountService).shouldHaveNoInteractions();
            then(jwtTokenProvider).shouldHaveNoInteractions();
            then(jwtRegistry).shouldHaveNoInteractions();
        }
    }

    private User stubLogin(SocialIdentity identity, String email) {
        UUID userId = UUID.randomUUID();
        User user = new User(email, null, identity.name());
        ReflectionTestUtils.setField(user, "id", userId);

        given(socialAccountService.resolveUser(identity)).willReturn(user);
        given(jwtTokenProvider.createAccessToken(userId)).willReturn(ACCESS_TOKEN);
        given(jwtTokenProvider.createRefreshToken(userId)).willReturn(REFRESH_TOKEN);

        return user;
    }

    private void assertLoginResponse(User user) {
        ArgumentCaptor<JwtInformation> captor = ArgumentCaptor.forClass(JwtInformation.class);
        then(jwtRegistry).should().registerJwtInformation(captor.capture());

        JwtInformation registered = captor.getValue();
        assertThat(registered.getUserDto().id()).isEqualTo(user.getId());
        assertThat(registered.getAccessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(registered.getRefreshToken()).isEqualTo(REFRESH_TOKEN);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/#/recommendations");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("REFRESH_TOKEN=" + REFRESH_TOKEN, "HttpOnly");
    }

    private OAuth2AuthenticationToken googleAuthentication() {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken("google-id-token", now, now.plusSeconds(300), Map.of(
                "sub", "google-user-123",
                "email", "user@example.com",
                "name", "구글 사용자",
                "email_verified", true
        ));
        DefaultOidcUser principal = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }
}