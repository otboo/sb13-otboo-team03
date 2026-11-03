
package com.otboo.team03.auth.integration;

import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mvc;

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
}
