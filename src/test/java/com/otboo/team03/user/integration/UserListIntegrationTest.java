package com.otboo.team03.user.integration;

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
class UserListIntegrationTest {

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
    @DisplayName("사용자 목록 조회 인증, 인가")
    class GetUserListAccessTest {

        @Test
        @DisplayName("ADMIN은 사용자 목록을 조회할 수 있다 - 200")
        void getUserList_admin_success() throws Exception {
            // given
            MvcResult adminLogin = login(adminProperties.email(), adminProperties.password());
            String adminAccessToken = getAccessToken(adminLogin);

            // when & then
            mockMvc.perform(get("/api/users")
                            .header("Authorization", "Bearer " + adminAccessToken)
                            .param("limit", "10")
                            .param("sortBy", "email")
                            .param("sortDirection", "ASCENDING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.hasNext").isBoolean())
                    .andExpect(jsonPath("$.totalCount").isNumber())
                    .andExpect(jsonPath("$.sortBy").value("email"))
                    .andExpect(jsonPath("$.sortDirection").value("ASCENDING"));
        }

        @Test
        @DisplayName("USER는 사용자 목록을 조회할 수 없다 - 403")
        void getUserList_user_returns403() throws Exception {
            // given
            User user = createUser();

            try {
                MvcResult userLogin = login(user.getEmail(), "password123!");
                String userAccessToken = getAccessToken(userLogin);

                // when & then
                mockMvc.perform(get("/api/users")
                                .header("Authorization", "Bearer " + userAccessToken)
                                .param("limit", "10")
                                .param("sortBy", "email")
                                .param("sortDirection", "ASCENDING"))
                        .andExpect(status().isForbidden());

            } finally {
                userRepository.deleteById(user.getId());
            }
        }

        @Test
        @DisplayName("비인증 사용자는 사용자 목록을 조회할 수 없다 - 401")
        void getUserList_unauthenticated_returns401() throws Exception {
            // when & then
            mockMvc.perform(get("/api/users")
                            .param("limit", "10")
                            .param("sortBy", "email")
                            .param("sortDirection", "ASCENDING"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // 테스트용 사용자 생성
    private User createUser() {
        String email = UUID.randomUUID() + "@example.com";
        return userRepository.saveAndFlush(
                new User(email, passwordEncoder.encode("password123!"), "일반 사용자")
        );
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
