
package com.otboo.team03.user.integration;

import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.RedisTemporaryPasswordRepository;
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
class UserPasswordIntegrationTest {

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
    private RedisTemporaryPasswordRepository temporaryPasswordRepository;

    @Nested
    @DisplayName("비밀번호 변경 접근 제어")
    class ChangePasswordAuthorizationTest {

        @Test
        @DisplayName("본인은 비밀번호를 변경할 수 있다 - 204")
        void changePassword_owner_returns204() throws Exception {
            // given
            String password = "password123!";
            User user = createUser(password);

            try {
                String accessToken = login(user.getEmail(), password);
                Cookie csrfCookie = getCsrfCookie();

                // when & then
                mockMvc.perform(patch("/api/users/{userId}/password", user.getId())
                                .header("Authorization", "Bearer " + accessToken)
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "password": "newPassword123!"
                                        }
                                        """))
                        .andExpect(status().isNoContent());

                // DB 비밀번호 변경 확인
                User updatedUser = userRepository.findById(user.getId()).orElseThrow();
                assertThat(passwordEncoder.matches("newPassword123!", updatedUser.getPassword())).isTrue();

            } finally {
                userRepository.deleteById(user.getId());
            }
        }

        @Test
        @DisplayName("다른 사용자의 비밀번호는 변경할 수 없다 - 403")
        void changePassword_otherUser_returns403() throws Exception {
            // given
            String password = "password123!";
            User owner = createUser(password);
            User otherUser = createUser(password);

            try {
                String otherAccessToken = login(otherUser.getEmail(), password);
                Cookie csrfCookie = getCsrfCookie();

                // when & then
                mockMvc.perform(patch("/api/users/{userId}/password", owner.getId())
                                .header("Authorization", "Bearer " + otherAccessToken)
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "password": "newPassword123!"
                                        }
                                        """))
                        .andExpect(status().isForbidden());

                // 기존 비밀번호 유지 확인
                User unchangedUser = userRepository.findById(owner.getId()).orElseThrow();
                assertThat(passwordEncoder.matches(password, unchangedUser.getPassword())).isTrue();

            } finally {
                userRepository.deleteById(owner.getId());
                userRepository.deleteById(otherUser.getId());
            }
        }

        @Test
        @DisplayName("비인증 사용자는 비밀번호를 변경할 수 없다 - 401")
        void changePassword_unauthenticated_returns401() throws Exception {
            // given
            Cookie csrfCookie = getCsrfCookie();

            // when & then
            mockMvc.perform(patch("/api/users/{userId}/password", UUID.randomUUID())
                            .cookie(csrfCookie)
                            .header("X-XSRF-TOKEN", csrfCookie.getValue())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "password": "newPassword123!"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("비밀번호 변경 후 인증정보 무효화")
    class ChangePasswordInvalidationTest {

        @Test
        @DisplayName("비밀번호 변경 후 기존 비밀번호와 임시 비밀번호 및 JWT가 무효화된다")
        void changePassword_invalidatesOldCredentials() throws Exception {
            // given
            String oldPassword = "oldPassword123!";
            String newPassword = "newPassword123!";
            String temporaryPassword = "temporary123!";
            User user = createUser(oldPassword);

            try {
                MvcResult loginResult = loginWithResponse(user.getEmail(), oldPassword);
                String oldAccessToken = extractAccessToken(loginResult);
                Cookie oldRefreshCookie = loginResult.getResponse().getCookie("REFRESH_TOKEN");

                assertThat(oldRefreshCookie).isNotNull();
                assertThat(oldRefreshCookie.getValue()).isNotBlank();

                temporaryPasswordRepository.save(
                        user.getEmail(),
                        passwordEncoder.encode(temporaryPassword)
                );

                assertThat(temporaryPasswordRepository.findByEmail(user.getEmail())).isPresent();

                Cookie csrfCookie = getCsrfCookie();

                // when
                mockMvc.perform(patch("/api/users/{userId}/password", user.getId())
                                .header("Authorization", "Bearer " + oldAccessToken)
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "password": "newPassword123!"
                                    }
                                    """))
                        .andExpect(status().isNoContent());

                // then: PostgreSQL 비밀번호 변경
                User updatedUser = userRepository.findById(user.getId()).orElseThrow();

                assertThat(passwordEncoder.matches(newPassword, updatedUser.getPassword())).isTrue();
                assertThat(passwordEncoder.matches(oldPassword, updatedUser.getPassword())).isFalse();

                // then: Redis 임시 비밀번호 삭제
                assertThat(temporaryPasswordRepository.findByEmail(user.getEmail())).isEmpty();

                // then: 기존 Access Token 무효화
                mockMvc.perform(patch("/api/users/{userId}/password", user.getId())
                                .header("Authorization", "Bearer " + oldAccessToken)
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "password": "anotherPassword123!"
                                    }
                                    """))
                        .andExpect(status().isUnauthorized());

                // then: 기존 Refresh Token 무효화
                Cookie refreshCsrfCookie = getCsrfCookie();

                mockMvc.perform(post("/api/auth/refresh")
                                .cookie(oldRefreshCookie, refreshCsrfCookie)
                                .header("X-XSRF-TOKEN", refreshCsrfCookie.getValue()))
                        .andExpect(status().isUnauthorized());

                // then: 기존 비밀번호 로그인 실패
                assertLoginFailed(user.getEmail(), oldPassword);

                // then: 임시 비밀번호 로그인 실패
                assertLoginFailed(user.getEmail(), temporaryPassword);

                // then: 새 비밀번호 로그인 성공
                String newAccessToken = login(user.getEmail(), newPassword);
                assertThat(newAccessToken).isNotBlank();

            } finally {
                temporaryPasswordRepository.delete(user.getEmail());
                userRepository.deleteById(user.getId());
            }
        }
    }

    @Nested
    @DisplayName("비밀번호 변경 요청 검증")
    class ChangePasswordValidationTest {

        @Test
        @DisplayName("CSRF 토큰 없이 비밀번호 변경 요청 시 403 반환")
        void changePassword_withoutCsrf_returns403() throws Exception {
            // given
            String password = "password123!";
            User user = createUser(password);

            try {
                String accessToken = login(user.getEmail(), password);

                // when & then
                mockMvc.perform(patch("/api/users/{userId}/password", user.getId())
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "password": "newPassword123!"
                                    }
                                    """))
                        .andExpect(status().isForbidden());

                User unchangedUser = userRepository.findById(user.getId()).orElseThrow();
                assertThat(passwordEncoder.matches(password, unchangedUser.getPassword())).isTrue();

            } finally {
                userRepository.deleteById(user.getId());
            }
        }

        @Test
        @DisplayName("빈 비밀번호 요청 시 400 반환")
        void changePassword_blankPassword_returns400() throws Exception {
            // given
            String password = "password123!";
            User user = createUser(password);

            try {
                String accessToken = login(user.getEmail(), password);
                Cookie csrfCookie = getCsrfCookie();

                // when & then
                mockMvc.perform(patch("/api/users/{userId}/password", user.getId())
                                .header("Authorization", "Bearer " + accessToken)
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "password": ""
                                    }
                                    """))
                        .andExpect(status().isBadRequest());

                User unchangedUser = userRepository.findById(user.getId()).orElseThrow();
                assertThat(passwordEncoder.matches(password, unchangedUser.getPassword())).isTrue();

            } finally {
                userRepository.deleteById(user.getId());
            }
        }
    }

    private User createUser(String password) {
        String email = UUID.randomUUID() + "@example.com";
        return userRepository.saveAndFlush(new User(email, passwordEncoder.encode(password), "테스트 사용자"));
    }

    private Cookie getCsrfCookie() throws Exception {
        Cookie csrfCookie = mockMvc.perform(get("/api/auth/csrf-token"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");

        assertThat(csrfCookie).isNotNull();
        return csrfCookie;
    }

    private String login(String email, String password) throws Exception {
        Cookie csrfCookie = getCsrfCookie();

        MvcResult result = mockMvc.perform(post("/api/auth/sign-in")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .param("username", email)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();

        assertThat(accessToken).isNotBlank();
        return accessToken;
    }

    private MvcResult loginWithResponse(String email, String password) throws Exception {
        Cookie csrfCookie = getCsrfCookie();

        return mockMvc.perform(post("/api/auth/sign-in")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .param("username", email)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String extractAccessToken(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
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
}
