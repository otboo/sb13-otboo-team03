
package com.otboo.team03.user.integration;

import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.init.AdminProperties;
import com.otboo.team03.user.repository.UserRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class UserLockIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminProperties adminProperties;

    @Autowired
    private JwtRegistry jwtRegistry;

    @Nested
    @DisplayName("계정 잠금, 해제 및 강제 로그아웃")
    class UpdateLockFlowTest {

        @Test
        @DisplayName("계정 잠금 시 기존 JWT가 무효화되고 로그인 차단, 잠금 해제 후 재로그인 성공")
        void updateLock_lockAndUnlock_invalidatesSession() throws Exception {
            // given
            String password = "password123!";
            User user = createUser(password);

            try {
                MvcResult userLogin = login(user.getEmail(), password);
                String userAccessToken = getAccessToken(userLogin);
                Cookie userRefreshCookie = userLogin.getResponse().getCookie("REFRESH_TOKEN");

                assertThat(userRefreshCookie).isNotNull();

                String adminAccessToken = getAccessToken(
                        login(adminProperties.email(), adminProperties.password()));
                Cookie csrfCookie = getCsrfCookie();

                // when: 관리자가 계정 잠금
                mockMvc.perform(patch("/api/users/{userId}/lock", user.getId())
                                .header("Authorization", "Bearer " + adminAccessToken)
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"locked": true}
                                        """))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(user.getId().toString()))
                        .andExpect(jsonPath("$.locked").value(true));

                // then: DB 잠금 상태 확인
                assertThat(userRepository.findById(user.getId()).orElseThrow().isLocked()).isTrue();

                // then: 기존 Access/Refresh Token 무효화
                assertThat(jwtRegistry.hasActiveJwtInformationByAccessToken(userAccessToken)).isFalse();
                assertThat(jwtRegistry.hasActiveJwtInformationByRefreshToken(userRefreshCookie.getValue())).isFalse();

                mockMvc.perform(get("/api/clothes")
                                .header("Authorization", "Bearer " + userAccessToken))
                        .andExpect(status().isUnauthorized());

                Cookie refreshCsrfCookie = getCsrfCookie();

                mockMvc.perform(post("/api/auth/refresh")
                                .cookie(userRefreshCookie, refreshCsrfCookie)
                                .header("X-XSRF-TOKEN", refreshCsrfCookie.getValue()))
                        .andExpect(status().isUnauthorized());

                // then: 잠긴 계정은 로그인 불가
                assertLoginFailed(user.getEmail(), password);

                // when: 관리자가 계정 잠금 해제
                Cookie unlockCsrfCookie = getCsrfCookie();

                mockMvc.perform(patch("/api/users/{userId}/lock", user.getId())
                                .header("Authorization", "Bearer " + adminAccessToken)
                                .cookie(unlockCsrfCookie)
                                .header("X-XSRF-TOKEN", unlockCsrfCookie.getValue())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"locked": false}
                                        """))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.locked").value(false));

                // then: 잠금 해제 후에도 기존 JWT는 복원되지 않음
                assertThat(userRepository.findById(user.getId()).orElseThrow().isLocked()).isFalse();
                assertThat(jwtRegistry.hasActiveJwtInformationByAccessToken(userAccessToken)).isFalse();
                assertThat(jwtRegistry.hasActiveJwtInformationByRefreshToken(userRefreshCookie.getValue())).isFalse();

                // then: 새 로그인 성공
                String newAccessToken = getAccessToken(login(user.getEmail(), password));
                assertThat(newAccessToken).isNotBlank();

            } finally {
                userRepository.deleteById(user.getId());
            }
        }

        @Test
        @DisplayName("동일한 잠금 상태 요청 시 기존 JWT 세션을 유지한다")
        void updateLock_sameState_keepsSession() throws Exception {
            // given
            User user = createUser();

            try {
                MvcResult userLogin = login(user.getEmail(), "password123!");
                String userAccessToken = getAccessToken(userLogin);
                Cookie userRefreshCookie = userLogin.getResponse().getCookie("REFRESH_TOKEN");

                assertThat(userRefreshCookie).isNotNull();

                String adminAccessToken = getAccessToken(
                        login(adminProperties.email(), adminProperties.password()));
                Cookie csrfCookie = getCsrfCookie();

                // when: 이미 잠금 해제된 사용자에게 동일한 상태 요청
                mockMvc.perform(patch("/api/users/{userId}/lock", user.getId())
                                .header("Authorization", "Bearer " + adminAccessToken)
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"locked": false}
                                        """))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.locked").value(false));

                // then
                assertThat(jwtRegistry.hasActiveJwtInformationByAccessToken(userAccessToken)).isTrue();
                assertThat(jwtRegistry.hasActiveJwtInformationByRefreshToken(userRefreshCookie.getValue())).isTrue();

            } finally {
                userRepository.deleteById(user.getId());
            }
        }
    }

    @Nested
    @DisplayName("계정 잠금 접근 제어")
    class UpdateLockAuthorizationTest {

        @Test
        @DisplayName("일반 사용자는 계정 잠금 요청을 할 수 없다 - 403")
        void updateLock_user_returns403() throws Exception {
            User user = createUser();

            try {
                String accessToken = getAccessToken(login(user.getEmail(), "password123!"));
                Cookie csrfCookie = getCsrfCookie();

                mockMvc.perform(patch("/api/users/{userId}/lock", user.getId())
                                .header("Authorization", "Bearer " + accessToken)
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"locked": true}
                                        """))
                        .andExpect(status().isForbidden());

                assertThat(userRepository.findById(user.getId()).orElseThrow().isLocked()).isFalse();

            } finally {
                userRepository.deleteById(user.getId());
            }
        }

        @Test
        @DisplayName("비인증 사용자는 계정 잠금 요청을 할 수 없다 - 401")
        void updateLock_unauthenticated_returns401() throws Exception {
            Cookie csrfCookie = getCsrfCookie();

            mockMvc.perform(patch("/api/users/{userId}/lock", UUID.randomUUID())
                            .cookie(csrfCookie)
                            .header("X-XSRF-TOKEN", csrfCookie.getValue())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"locked": true}
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("관리자는 자기 계정을 잠글 수 없다 - 403")
        void updateLock_selfLock_returns403() throws Exception {
            // given
            User admin = userRepository.findByEmail(adminProperties.email())
                    .orElseThrow();

            String adminAccessToken = getAccessToken(
                    login(adminProperties.email(), adminProperties.password()));
            Cookie csrfCookie = getCsrfCookie();

            // when & then
            mockMvc.perform(patch("/api/users/{userId}/lock", admin.getId())
                            .header("Authorization", "Bearer " + adminAccessToken)
                            .cookie(csrfCookie)
                            .header("X-XSRF-TOKEN", csrfCookie.getValue())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                            {"locked": true}
                            """))
                    .andExpect(status().isForbidden());

            assertThat(userRepository.findById(admin.getId()).orElseThrow().isLocked())
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("계정 잠금 요청 검증")
    class UpdateLockValidationTest {

        @Test
        @DisplayName("존재하지 않는 사용자면 404 반환")
        void updateLock_userNotFound_returns404() throws Exception {
            String adminAccessToken = getAccessToken(
                    login(adminProperties.email(), adminProperties.password()));
            Cookie csrfCookie = getCsrfCookie();

            mockMvc.perform(patch("/api/users/{userId}/lock", UUID.randomUUID())
                            .header("Authorization", "Bearer " + adminAccessToken)
                            .cookie(csrfCookie)
                            .header("X-XSRF-TOKEN", csrfCookie.getValue())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"locked": true}
                                    """))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("locked 필드 누락 시 400 반환")
        void updateLock_missingLocked_returns400() throws Exception {
            String adminAccessToken = getAccessToken(
                    login(adminProperties.email(), adminProperties.password()));
            Cookie csrfCookie = getCsrfCookie();

            mockMvc.perform(patch("/api/users/{userId}/lock", UUID.randomUUID())
                            .header("Authorization", "Bearer " + adminAccessToken)
                            .cookie(csrfCookie)
                            .header("X-XSRF-TOKEN", csrfCookie.getValue())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("CSRF 토큰 누락 시 403 반환")
        void updateLock_withoutCsrf_returns403() throws Exception {
            String adminAccessToken = getAccessToken(
                    login(adminProperties.email(), adminProperties.password()));

            mockMvc.perform(patch("/api/users/{userId}/lock", UUID.randomUUID())
                            .header("Authorization", "Bearer " + adminAccessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"locked": true}
                                    """))
                    .andExpect(status().isForbidden());
        }
    }

    private User createUser() {
        return createUser("password123!");
    }

    private User createUser(String password) {
        String email = UUID.randomUUID() + "@example.com";
        return userRepository.saveAndFlush(
                new User(email, passwordEncoder.encode(password), "일반 사용자"));
    }

    private Cookie getCsrfCookie() throws Exception {
        Cookie csrfCookie = mockMvc.perform(get("/api/auth/csrf-token"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");

        assertThat(csrfCookie).isNotNull();
        return csrfCookie;
    }

    private MvcResult login(String email, String password) throws Exception {
        Cookie csrfCookie = getCsrfCookie();

        return mockMvc.perform(post("/api/auth/sign-in")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .param("username", email)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn();
    }

    private void assertLoginFailed(String email, String password) throws Exception {
        Cookie csrfCookie = getCsrfCookie();

        mockMvc.perform(post("/api/auth/sign-in")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .param("username", email)
                        .param("password", password))
                .andExpect(status().isUnauthorized());
    }

    private String getAccessToken(MvcResult loginResult) throws Exception {
        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        assertThat(accessToken).isNotBlank();
        return accessToken;
    }
}
