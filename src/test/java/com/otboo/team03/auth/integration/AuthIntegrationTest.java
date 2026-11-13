package com.otboo.team03.auth.integration;

import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.RedisTemporaryPasswordRepository;
import com.otboo.team03.user.repository.UserRepository;
import com.otboo.team03.user.service.MailService;
import com.otboo.team03.auth.entity.SocialAccount;
import com.otboo.team03.auth.entity.SocialProvider;
import com.otboo.team03.auth.repository.SocialAccountRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthIntegrationTest {

    // 테스트 전용 PostgreSQL 사용
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    // 테스트 전용 Redis 사용
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private RedisTemporaryPasswordRepository temporaryPasswordRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MailService mailService;

    @Nested
    @DisplayName("인증 및 CSRF 보안")
    class SecurityTest {

        @Test
        @DisplayName("비로그인 사용자가 보호된 API에 접근하면 401을 반환한다")
        void unauthenticatedRequest_returns401() throws Exception {
            mvc.perform(get("/api/clothes"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("CSRF 토큰 없이 로그인 요청 시 403을 반환한다")
        void requestWithoutCsrf_returns403() throws Exception {
            mvc.perform(post("/api/auth/sign-in")
                            .param("username", "test@example.com")
                            .param("password", "password"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("CSRF 토큰 발급 API는 204와 XSRF-TOKEN 쿠키를 반환한다")
        void csrfTokenEndpoint_returns204() throws Exception {
            mvc.perform(get("/api/auth/csrf-token"))
                    .andExpect(status().isNoContent())
                    .andExpect(cookie().exists("XSRF-TOKEN"));
        }
    }

    @Nested
    @DisplayName("로그인")
    class LoginTest {

        @Test
        @DisplayName("정상 로그인 시 Access Token과 Refresh Token 쿠키를 발급한다")
        void login_success_returns200AndJwt() throws Exception {
            // given
            String email = UUID.randomUUID() + "@example.com";
            String password = "password123!";

            User user = userRepository.saveAndFlush(new User(email, passwordEncoder.encode(password), "테스트 사용자"));

            try {
                // CSRF 토큰 발급
                Cookie csrfCookie = mvc.perform(get("/api/auth/csrf-token"))
                        .andExpect(status().isNoContent())
                        .andReturn().getResponse().getCookie("XSRF-TOKEN");

                assertThat(csrfCookie).isNotNull();

                // when & then
                mvc.perform(post("/api/auth/sign-in")
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .param("username", email)
                                .param("password", password))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.accessToken").isNotEmpty())
                        .andExpect(cookie().exists("REFRESH_TOKEN"))
                        .andExpect(cookie().httpOnly("REFRESH_TOKEN", true))
                        .andExpect(cookie().path("REFRESH_TOKEN", "/"));

            } finally {
                userRepository.deleteById(user.getId());
            }
        }

        @Test
        @DisplayName("임시 비밀번호가 있어도 기존 비밀번호로 로그인할 수 있고, 임시 비밀번호는 한 번만 사용할 수 있다")
        void login_withTemporaryPassword_allowsOriginalAndConsumesTemporaryOnce() throws Exception {
            // given
            String email = UUID.randomUUID() + "@example.com";
            String originalPassword = "password123!";
            String temporaryPassword = "temporary1!!";

            User user = userRepository.saveAndFlush(
                    new User(email, passwordEncoder.encode(originalPassword), "테스트 사용자")
            );

            try {
                temporaryPasswordRepository.save(email, passwordEncoder.encode(temporaryPassword));

                // 기존 비밀번호 로그인 성공
                Cookie csrfCookie = getCsrfCookie();

                mvc.perform(post("/api/auth/sign-in")
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .param("username", email)
                                .param("password", originalPassword))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.accessToken").isNotEmpty())
                        .andExpect(cookie().exists("REFRESH_TOKEN"));

                // 기존 비밀번호 로그인은 임시 비밀번호를 소모하지 않는다.
                assertThat(temporaryPasswordRepository.findByEmail(email)).isPresent();

                // 임시 비밀번호 최초 로그인 성공
                csrfCookie = getCsrfCookie();

                mvc.perform(post("/api/auth/sign-in")
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .param("username", email)
                                .param("password", temporaryPassword))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.accessToken").isNotEmpty())
                        .andExpect(cookie().exists("REFRESH_TOKEN"));

                // 성공한 임시 비밀번호는 Redis에서 삭제된다.
                assertThat(temporaryPasswordRepository.findByEmail(email)).isEmpty();

                // 임시 비밀번호 재사용 시 로그인 실패
                csrfCookie = getCsrfCookie();

                mvc.perform(post("/api/auth/sign-in")
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .param("username", email)
                                .param("password", temporaryPassword))
                        .andExpect(status().isUnauthorized());

            } finally {
                temporaryPasswordRepository.delete(email);
                userRepository.deleteById(user.getId());
            }
        }

        @Test
        @DisplayName("임시 비밀번호 만료 후 기존 비밀번호로 다시 로그인할 수 있다")
        void login_afterTemporaryPasswordExpiration_acceptsOriginalPassword() throws Exception {
            // given
            String email = UUID.randomUUID() + "@example.com";
            String originalPassword = "password123!";
            String temporaryPassword = "temporary1!!";

            User user = userRepository.saveAndFlush(new User(email, passwordEncoder.encode(originalPassword), "테스트 사용자"));

            try {
                temporaryPasswordRepository.save(email, passwordEncoder.encode(temporaryPassword));

                // Redis TTL 만료 상태 재현
                temporaryPasswordRepository.delete(email);

                Cookie csrfCookie = mvc.perform(get("/api/auth/csrf-token"))
                        .andExpect(status().isNoContent())
                        .andReturn().getResponse().getCookie("XSRF-TOKEN");

                assertThat(csrfCookie).isNotNull();

                // 기존 비밀번호 로그인 성공
                mvc.perform(post("/api/auth/sign-in")
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .param("username", email)
                                .param("password", originalPassword))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.accessToken").isNotEmpty());

                // 만료된 임시 비밀번호 로그인 실패
                mvc.perform(post("/api/auth/sign-in")
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .param("username", email)
                                .param("password", temporaryPassword))
                        .andExpect(status().isUnauthorized());

            } finally {
                temporaryPasswordRepository.delete(email);
                userRepository.deleteById(user.getId());
            }
        }
    }

    @Nested
    @DisplayName("토큰 재발급")
    class RefreshTest {

        @Test
        @DisplayName("Refresh Token으로 Access Token과 Refresh Token을 재발급한다")
        void refresh_success_rotatesTokens() throws Exception {
            // given
            String email = UUID.randomUUID() + "@example.com";
            String password = "password123!";

            User user = userRepository.saveAndFlush(new User(email, passwordEncoder.encode(password), "테스트 사용자"));

            try {
                // 1. CSRF 토큰 발급
                Cookie csrfCookie = mvc.perform(get("/api/auth/csrf-token"))
                        .andExpect(status().isNoContent())
                        .andReturn().getResponse().getCookie("XSRF-TOKEN");

                assertThat(csrfCookie).isNotNull();

                // 2. 로그인
                MvcResult loginResult = mvc.perform(post("/api/auth/sign-in")
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .param("username", email)
                                .param("password", password))
                        .andExpect(status().isOk())
                        .andExpect(cookie().exists("REFRESH_TOKEN"))
                        .andReturn();

                Cookie refreshCookie = loginResult.getResponse().getCookie("REFRESH_TOKEN");
                assertThat(refreshCookie).isNotNull();

                // 로그인 이후 CSRF 토큰 새로 발급
                Cookie newCsrfCookie = mvc.perform(get("/api/auth/csrf-token"))
                        .andExpect(status().isNoContent())
                        .andReturn().getResponse().getCookie("XSRF-TOKEN");

                assertThat(newCsrfCookie).isNotNull();
                assertThat(newCsrfCookie.getValue()).isNotEmpty();

                // 3. Refresh Token 재발급
                mvc.perform(post("/api/auth/refresh")
                                .cookie(refreshCookie, newCsrfCookie)
                                .header("X-XSRF-TOKEN", newCsrfCookie.getValue()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.accessToken").isNotEmpty())
                        .andExpect(cookie().exists("REFRESH_TOKEN"));

            } finally {
                userRepository.deleteById(user.getId());
            }
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class LogoutTest {

        @Test
        @DisplayName("로그아웃 후 기존 Access Token을 사용할 수 없다")
        void logout_invalidatesAccessToken() throws Exception {
            // given
            String email = UUID.randomUUID() + "@example.com";
            String password = "password123!";

            User user = userRepository.saveAndFlush(new User(email, passwordEncoder.encode(password), "테스트 사용자"));

            try {
                // 1. CSRF 토큰 발급
                Cookie csrfCookie = mvc.perform(get("/api/auth/csrf-token"))
                        .andExpect(status().isNoContent())
                        .andReturn().getResponse().getCookie("XSRF-TOKEN");

                assertThat(csrfCookie).isNotNull();

                // 2. 로그인
                MvcResult loginResult = mvc.perform(post("/api/auth/sign-in")
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .param("username", email)
                                .param("password", password))
                        .andExpect(status().isOk())
                        .andReturn();

                JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
                String accessToken = loginBody.get("accessToken").asText();
                Cookie refreshCookie = loginResult.getResponse().getCookie("REFRESH_TOKEN");

                assertThat(accessToken).isNotBlank();
                assertThat(refreshCookie).isNotNull();

                // 3. 로그인 이후 CSRF 토큰 새로 발급
                Cookie newCsrfCookie = mvc.perform(get("/api/auth/csrf-token"))
                        .andExpect(status().isNoContent())
                        .andReturn().getResponse().getCookie("XSRF-TOKEN");

                assertThat(newCsrfCookie).isNotNull();

                // 로그아웃 전 기존 Access Token 사용 확인
                mvc.perform(get("/api/clothes")
                                .header("Authorization", "Bearer " + accessToken))
                        .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));

                // 4. 로그아웃
                mvc.perform(post("/api/auth/sign-out")
                                .cookie(refreshCookie, newCsrfCookie)
                                .header("X-XSRF-TOKEN", newCsrfCookie.getValue())
                                .header("Authorization", "Bearer " + accessToken))
                        .andExpect(status().isNoContent());

                // 5. 로그아웃 후 기존 Access Token 거부
                mvc.perform(get("/api/clothes")
                                .header("Authorization", "Bearer " + accessToken))
                        .andExpect(status().isUnauthorized());

            } finally {
                userRepository.deleteById(user.getId());
            }
        }
    }

    @Nested
    @DisplayName("비밀번호 초기화")
    class ResetPasswordTest {

        @Test
        @DisplayName("성공 - 비인증 사용자가 초기화 요청 시 204 반환 및 임시 비밀번호 발급")
        void resetPassword_success_returns204() throws Exception {
            // given
            String email = UUID.randomUUID() + "@example.com";
            User user = userRepository.saveAndFlush(new User(email, passwordEncoder.encode("password123!"), "테스트 사용자"));

            try {
                Cookie csrfCookie = getCsrfCookie();

                // when & then
                mvc.perform(post("/api/auth/reset-password")
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "email": "%s"
                                    }
                                    """.formatted(email)))
                        .andExpect(status().isNoContent());

                assertThat(temporaryPasswordRepository.findByEmail(email)).isPresent();
                verify(mailService).sendTemporaryPassword(eq(email), anyString());

            } finally {
                temporaryPasswordRepository.delete(email);
                userRepository.deleteById(user.getId());
            }
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 이메일이면 404 반환")
        void resetPassword_userNotFound_returns404() throws Exception {
            // given
            String email = UUID.randomUUID() + "@example.com";
            Cookie csrfCookie = getCsrfCookie();

            // when & then
            mvc.perform(post("/api/auth/reset-password")
                            .cookie(csrfCookie)
                            .header("X-XSRF-TOKEN", csrfCookie.getValue())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "email": "%s"
                                }
                                """.formatted(email)))
                    .andExpect(status().isNotFound());

            assertThat(temporaryPasswordRepository.findByEmail(email)).isEmpty();
        }

        @Test
        @DisplayName("실패 - CSRF 토큰 없이 초기화 요청 시 403 반환")
        void resetPassword_withoutCsrf_returns403() throws Exception {
            // given
            String email = "user@example.com";

            // when & then
            mvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "email": "%s"
                                }
                                """.formatted(email)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("실패 - 잘못된 이메일 형식이면 400 반환")
        void resetPassword_invalidEmail_returns400() throws Exception {
            // given
            Cookie csrfCookie = getCsrfCookie();

            // when & then
            mvc.perform(post("/api/auth/reset-password")
                            .cookie(csrfCookie)
                            .header("X-XSRF-TOKEN", csrfCookie.getValue())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "email": "invalid-email"
                                }
                                """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("카카오 소셜 계정은 비밀번호 초기화를 요청해도 임시 비밀번호를 발급하거나 발송하지 않는다")
        void resetPassword_kakaoAccount_returns204WithoutIssuingTemporaryPassword() throws Exception {
            // given
            String providerUserId = UUID.randomUUID().toString();
            String email = "kakao_" + providerUserId + "@kakao.com";
            User user = userRepository.saveAndFlush(new User(email, null, "카카오 사용자"));

            try {
                socialAccountRepository.saveAndFlush(new SocialAccount(user, SocialProvider.KAKAO, providerUserId));
                Cookie csrfCookie = getCsrfCookie();

                // when & then
                mvc.perform(post("/api/auth/reset-password")
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                            {
                                "email": "%s"
                            }
                            """.formatted(email)))
                        .andExpect(status().isNoContent());

                assertThat(temporaryPasswordRepository.findByEmail(email)).isEmpty();
                verify(mailService, never()).sendTemporaryPassword(eq(email), anyString());
            } finally {
                temporaryPasswordRepository.delete(email);
                socialAccountRepository.deleteById(user.getId());
                userRepository.deleteById(user.getId());
            }
        }
    }

    private Cookie getCsrfCookie() throws Exception {
        Cookie csrfCookie = mvc.perform(get("/api/auth/csrf-token"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");

        assertThat(csrfCookie).isNotNull();
        return csrfCookie;
    }
}
