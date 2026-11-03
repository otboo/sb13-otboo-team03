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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
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

    @Nested
    @DisplayName("사용자 목록 커서 페이지네이션")
    class GetUserListPaginationTest {

        @Test
        @DisplayName("첫 페이지와 다음 페이지를 중복 없이 조회하고 전체 건수를 유지한다")
        void getUserList_cursorPagination_success() throws Exception {
            // given
            String keyword = "list-page-" + UUID.randomUUID();
            User alice = createUser("alice." + keyword + "@example.com");
            User bob = createUser("bob." + keyword + "@example.com");
            User charlie = createUser("charlie." + keyword + "@example.com");

            try {
                MvcResult adminLogin = login(adminProperties.email(), adminProperties.password());
                String accessToken = getAccessToken(adminLogin);

                // when: 첫 페이지 조회
                MvcResult firstResult = mockMvc.perform(get("/api/users")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("limit", "2")
                                .param("sortBy", "email")
                                .param("sortDirection", "ASCENDING")
                                .param("emailLike", keyword))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.length()").value(2))
                        .andExpect(jsonPath("$.data[0].email").value(alice.getEmail()))
                        .andExpect(jsonPath("$.data[1].email").value(bob.getEmail()))
                        .andExpect(jsonPath("$.hasNext").value(true))
                        .andExpect(jsonPath("$.nextCursor").value(bob.getEmail()))
                        .andExpect(jsonPath("$.nextIdAfter").value(bob.getId().toString()))
                        .andExpect(jsonPath("$.totalCount").value(3))
                        .andReturn();

                String nextCursor = objectMapper.readTree(firstResult.getResponse().getContentAsString())
                        .get("nextCursor").asText();

                String nextIdAfter = objectMapper.readTree(firstResult.getResponse().getContentAsString())
                        .get("nextIdAfter").asText();

                // when & then: 다음 페이지 조회
                mockMvc.perform(get("/api/users")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("cursor", nextCursor)
                                .param("idAfter", nextIdAfter)
                                .param("limit", "2")
                                .param("sortBy", "email")
                                .param("sortDirection", "ASCENDING")
                                .param("emailLike", keyword))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.length()").value(1))
                        .andExpect(jsonPath("$.data[0].email").value(charlie.getEmail()))
                        .andExpect(jsonPath("$.hasNext").value(false))
                        .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                        .andExpect(jsonPath("$.nextIdAfter").value(nullValue()))
                        .andExpect(jsonPath("$.totalCount").value(3));

            } finally {
                userRepository.deleteAllById(List.of(alice.getId(), bob.getId(), charlie.getId()));
            }
        }

        @Test
        @DisplayName("조회 결과가 없으면 빈 목록과 null 커서를 반환한다")
        void getUserList_emptyResult() throws Exception {
            // given
            String keyword = "not-found-" + UUID.randomUUID();
            String accessToken = getAccessToken(login(adminProperties.email(), adminProperties.password()));

            // when & then
            mockMvc.perform(get("/api/users")
                            .header("Authorization", "Bearer " + accessToken)
                            .param("limit", "10")
                            .param("sortBy", "email")
                            .param("sortDirection", "ASCENDING")
                            .param("emailLike", keyword))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty())
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                    .andExpect(jsonPath("$.nextIdAfter").value(nullValue()))
                    .andExpect(jsonPath("$.totalCount").value(0));
        }
    }

    @Nested
    @DisplayName("사용자 목록 복합 필터링")
    class GetUserListFilterTest {

        @Test
        @DisplayName("이메일, 권한, 잠금 상태 필터를 AND 조건으로 적용한다")
        void getUserList_combinedFilters() throws Exception {
            // given
            String keyword = "list-filter-" + UUID.randomUUID();
            User target = createUser("target." + keyword + "@example.com");
            User unlockedUser = createUser("unlocked." + keyword + "@example.com");
            User adminUser = createUser("admin." + keyword + "@example.com");
            User otherUser = createUser("other-" + UUID.randomUUID() + "@example.com");

            try {
                adminUser.changeRole(UserRole.ADMIN);
                ReflectionTestUtils.setField(target, "locked", true);
                ReflectionTestUtils.setField(adminUser, "locked", true);
                ReflectionTestUtils.setField(otherUser, "locked", true);
                userRepository.saveAllAndFlush(List.of(target, adminUser, otherUser));

                String accessToken = getAccessToken(login(adminProperties.email(), adminProperties.password()));

                // when & then
                mockMvc.perform(get("/api/users")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("limit", "10")
                                .param("sortBy", "email")
                                .param("sortDirection", "ASCENDING")
                                .param("emailLike", keyword)
                                .param("roleEqual", "USER")
                                .param("locked", "true"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.length()").value(1))
                        .andExpect(jsonPath("$.data[0].email").value(target.getEmail()))
                        .andExpect(jsonPath("$.data[0].role").value("USER"))
                        .andExpect(jsonPath("$.data[0].locked").value(true))
                        .andExpect(jsonPath("$.hasNext").value(false))
                        .andExpect(jsonPath("$.totalCount").value(1));

            } finally {
                userRepository.deleteAllById(List.of(target.getId(), unlockedUser.getId(), adminUser.getId(), otherUser.getId()));
            }
        }
    }

    @Nested
    @DisplayName("사용자 목록 요청 검증")
    class GetUserListValidationTest {

        @Test
        @DisplayName("limit이 0이면 400 Bad Request를 반환한다")
        void getUserList_invalidLimit() throws Exception {
            String accessToken = getAccessToken(login(adminProperties.email(), adminProperties.password()));

            mockMvc.perform(get("/api/users")
                            .header("Authorization", "Bearer " + accessToken)
                            .param("limit", "0")
                            .param("sortBy", "email")
                            .param("sortDirection", "ASCENDING"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("잘못된 정렬 방향이면 400 Bad Request를 반환한다")
        void getUserList_invalidSortDirection() throws Exception {
            String accessToken = getAccessToken(login(adminProperties.email(), adminProperties.password()));

            mockMvc.perform(get("/api/users")
                            .header("Authorization", "Bearer " + accessToken)
                            .param("limit", "10")
                            .param("sortBy", "email")
                            .param("sortDirection", "INVALID"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("잘못된 UUID 형식이면 400 Bad Request를 반환한다")
        void getUserList_invalidIdAfter() throws Exception {
            String accessToken = getAccessToken(login(adminProperties.email(), adminProperties.password()));

            mockMvc.perform(get("/api/users")
                            .header("Authorization", "Bearer " + accessToken)
                            .param("limit", "10")
                            .param("sortBy", "email")
                            .param("sortDirection", "ASCENDING")
                            .param("idAfter", "invalid-uuid"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("잘못된 createdAt 커서 형식이면 400 Bad Request를 반환한다")
        void getUserList_invalidCreatedAtCursor() throws Exception {
            String accessToken = getAccessToken(login(adminProperties.email(), adminProperties.password()));

            mockMvc.perform(get("/api/users")
                            .header("Authorization", "Bearer " + accessToken)
                            .param("limit", "10")
                            .param("sortBy", "createdAt")
                            .param("sortDirection", "ASCENDING")
                            .param("cursor", "invalid-date"))
                    .andExpect(status().isBadRequest());
        }
    }

    // 테스트용 사용자 생성
    private User createUser() {
        return createUser(UUID.randomUUID() + "@example.com");
    }

    private User createUser(String email) {
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
