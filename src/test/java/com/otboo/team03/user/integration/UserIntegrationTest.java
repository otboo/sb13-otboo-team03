
package com.otboo.team03.user.integration;

import tools.jackson.databind.ObjectMapper;
import com.otboo.team03.user.dto.request.UserCreateRequest;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.entity.UserRole;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Testcontainers
class UserIntegrationTest {

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

    @Test
    @DisplayName("회원가입 성공 - 사용자 DB 저장 및 비밀번호 암호화")
    void createUser_success() throws Exception {
        // given
        String email = "integration-" + UUID.randomUUID() + "@example.com";
        String rawPassword = "password123!";

        UserCreateRequest request = new UserCreateRequest("홍길동", email, rawPassword);

        // when & then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.locked").value(false));

        // DB 저장 검증
        User savedUser = userRepository.findByEmail(email).orElseThrow();

        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getRole()).isEqualTo(UserRole.USER);
        assertThat(savedUser.isLocked()).isFalse();

        // 비밀번호 암호화 검증
        assertThat(savedUser.getPassword()).isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, savedUser.getPassword())).isTrue();
    }
}
