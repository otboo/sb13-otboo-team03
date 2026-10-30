
package com.otboo.team03.user.integration;

import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.entity.UserRole;
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
class UserRoleIntegrationTest {

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

    @Nested
    @DisplayName("사용자 권한 변경 접근 제어")
    class UpdateRoleTest {

        @Test
        @DisplayName("ADMIN은 사용자 권한을 변경할 수 있다")
        void updateRole_admin_success() throws Exception {
            // given
            User user = createUser();

            try {
                MvcResult adminLogin = login(adminProperties.email(), adminProperties.password());
                String adminAccessToken = getAccessToken(adminLogin);
                Cookie csrfCookie = getCsrfCookie();

                // when & then
                mockMvc.perform(patch("/api/users/{userId}/role", user.getId())
                                .header("Authorization", "Bearer " + adminAccessToken)
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "role": "ADMIN"
                                        }
                                        """))
                        .andExpect(status().isNoContent());

                assertThat(userRepository.findById(user.getId()).orElseThrow().getRole())
                        .isEqualTo(UserRole.ADMIN);

            } finally {
                userRepository.deleteById(user.getId());
            }
        }

        @Test
        @DisplayName("USER는 사용자 권한을 변경할 수 없다 - 403")
        void updateRole_user_returns403() throws Exception {
            // given
            String password = "password123!";
            User user = createUser(password);

            try {
                MvcResult userLogin = login(user.getEmail(), password);
                String userAccessToken = getAccessToken(userLogin);
                Cookie csrfCookie = getCsrfCookie();

                // when & then
                mockMvc.perform(patch("/api/users/{userId}/role", user.getId())
                                .header("Authorization", "Bearer " + userAccessToken)
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "role": "ADMIN"
                                        }
                                        """))
                        .andExpect(status().isForbidden());

                assertThat(userRepository.findById(user.getId()).orElseThrow().getRole())
                        .isEqualTo(UserRole.USER);

            } finally {
                userRepository.deleteById(user.getId());
            }
        }

        @Test
        @DisplayName("비인증 사용자는 권한 변경 API에 접근할 수 없다 - 401")
        void updateRole_unauthenticated_returns401() throws Exception {
            // given
            UUID userId = UUID.randomUUID();
            Cookie csrfCookie = getCsrfCookie();

            // when & then
            mockMvc.perform(patch("/api/users/{userId}/role", userId)
                            .cookie(csrfCookie)
                            .header("X-XSRF-TOKEN", csrfCookie.getValue())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "role": "ADMIN"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("권한 변경 시 JWT 세션 무효화")
    class InvalidateJwtTest {

        @Test
        @DisplayName("권한 변경 후 기존 Access/Refresh Token을 사용할 수 없다")
        void updateRole_roleChanged_invalidatesExistingTokens() throws Exception {
            // given
            String password = "password123!";
            User user = createUser(password);

            try {
                MvcResult userLogin = login(user.getEmail(), password);
                String userAccessToken = getAccessToken(userLogin);
                Cookie userRefreshCookie = userLogin.getResponse().getCookie("REFRESH_TOKEN");

                assertThat(userRefreshCookie).isNotNull();

                MvcResult adminLogin = login(adminProperties.email(), adminProperties.password());
                String adminAccessToken = getAccessToken(adminLogin);
                Cookie csrfCookie = getCsrfCookie();

                // when: 관리자가 사용자 권한 변경
                mockMvc.perform(patch("/api/users/{userId}/role", user.getId())
                                .header("Authorization", "Bearer " + adminAccessToken)
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "role": "ADMIN"
                                        }
                                        """))
                        .andExpect(status().isNoContent());

                // then: 기존 Access Token 사용 불가
                mockMvc.perform(get("/api/clothes")
                                .header("Authorization", "Bearer " + userAccessToken))
                        .andExpect(status().isUnauthorized());

                // 기존 Refresh Token으로 재발급 불가
                Cookie refreshCsrfCookie = getCsrfCookie();

                mockMvc.perform(post("/api/auth/refresh")
                                .cookie(userRefreshCookie, refreshCsrfCookie)
                                .header("X-XSRF-TOKEN", refreshCsrfCookie.getValue()))
                        .andExpect(status().isUnauthorized());

            } finally {
                userRepository.deleteById(user.getId());
            }
        }
    }

    // 테스트용 사용자 생성
    private User createUser() {
        return createUser("password123!");
    }

    private User createUser(String password) {
        String email = UUID.randomUUID() + "@example.com";
        return userRepository.saveAndFlush(new User(email, passwordEncoder.encode(password), "일반 사용자"));
    }

    // CSRF 토큰 발급
    private Cookie getCsrfCookie() throws Exception {
        Cookie csrfCookie = mockMvc.perform(get("/api/auth/csrf-token"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");

        assertThat(csrfCookie).isNotNull();
        return csrfCookie;
    }

    // 로그인
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

    // Access Token 추출
    private String getAccessToken(MvcResult loginResult) throws Exception {
        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        assertThat(accessToken).isNotBlank();
        return accessToken;
    }
}
