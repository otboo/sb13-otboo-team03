
package com.otboo.team03.user.integration;

import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.entity.UserRole;
import com.otboo.team03.user.init.AdminProperties;
import com.otboo.team03.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class UserAdminConcurrencyIntegrationTest {

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

    private User adminA;
    private User adminB;
    private String accessTokenA;
    private String accessTokenB;

    @BeforeEach
    void setUp() throws Exception {
        adminA = userRepository.findByEmail(adminProperties.email()).orElseThrow();

        String passwordB = "password123!";
        adminB = new User(UUID.randomUUID() + "@example.com", passwordEncoder.encode(passwordB), "관리자 B");
        adminB.changeRole(UserRole.ADMIN);
        adminB = userRepository.saveAndFlush(adminB);

        accessTokenA = getAccessToken(login(adminA.getEmail(), adminProperties.password()));
        accessTokenB = getAccessToken(login(adminB.getEmail(), passwordB));
    }

    @AfterEach
    void tearDown() {
        if (adminA != null) {
            User admin = userRepository.findById(adminA.getId()).orElseThrow();
            admin.changeRole(UserRole.ADMIN);
            admin.changeLocked(false);
            userRepository.saveAndFlush(admin);
        }

        if (adminB != null) {
            userRepository.deleteById(adminB.getId());
        }
    }

    @Nested
    @DisplayName("마지막 ADMIN 동시성 보호")
    class AdminConcurrencyTest {

        @Test
        @DisplayName("두 ADMIN이 서로를 동시에 강등해도 ADMIN이 최소 1명 유지된다")
        void updateRole_concurrentDemotion_keepsAdmin() throws Exception {
            // when
            List<Integer> statuses = executeConcurrently(
                    () -> updateRole(adminB.getId(), accessTokenA),
                    () -> updateRole(adminA.getId(), accessTokenB)
            );

            // then
            assertThat(statuses).allMatch(status -> status >= 200 && status < 500);
            assertThat(userRepository.countByRole(UserRole.ADMIN)).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("두 ADMIN을 동시에 잠가도 활성 ADMIN이 최소 1명 유지된다")
        void updateLock_concurrentLock_keepsActiveAdmin() throws Exception {
            // when
            List<Integer> statuses = executeConcurrently(
                    () -> updateLock(adminB.getId(), accessTokenA),
                    () -> updateLock(adminA.getId(), accessTokenB)
            );

            // then
            assertThat(statuses).allMatch(status -> status >= 200 && status < 500);
            assertThat(userRepository.countByRoleAndLockedFalse(UserRole.ADMIN)).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("ADMIN 강등과 잠금이 동시에 발생해도 활성 ADMIN이 최소 1명 유지된다")
        void updateRoleAndLock_concurrentRequests_keepsActiveAdmin() throws Exception {
            // when
            List<Integer> statuses = executeConcurrently(
                    () -> updateRole(adminB.getId(), accessTokenA),
                    () -> updateLock(adminA.getId(), accessTokenB)
            );

            // then
            assertThat(statuses).allMatch(status -> status >= 200 && status < 500);
            assertThat(userRepository.countByRoleAndLockedFalse(UserRole.ADMIN)).isGreaterThanOrEqualTo(1);
        }
    }

    private List<Integer> executeConcurrently(Callable<Integer> requestA, Callable<Integer> requestB) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Integer> resultA = executor.submit(() -> executeRequest(requestA, ready, start));
            Future<Integer> resultB = executor.submit(() -> executeRequest(requestB, ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            return List.of(
                    resultA.get(15, TimeUnit.SECONDS),
                    resultB.get(15, TimeUnit.SECONDS)
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private int executeRequest(
            Callable<Integer> request,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();
        return request.call();
    }

    private int updateRole(UUID userId, String accessToken) throws Exception {
        Cookie csrfCookie = getCsrfCookie();

        return mockMvc.perform(patch("/api/users/{userId}/role", userId)
                        .header("Authorization", "Bearer " + accessToken)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "USER"}
                                """))
                .andReturn().getResponse().getStatus();
    }

    private int updateLock(UUID userId, String accessToken) throws Exception {
        Cookie csrfCookie = getCsrfCookie();

        return mockMvc.perform(patch("/api/users/{userId}/lock", userId)
                        .header("Authorization", "Bearer " + accessToken)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"locked": true}
                                """))
                .andReturn().getResponse().getStatus();
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

    private String getAccessToken(MvcResult loginResult) throws Exception {
        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        assertThat(accessToken).isNotBlank();
        return accessToken;
    }
}
