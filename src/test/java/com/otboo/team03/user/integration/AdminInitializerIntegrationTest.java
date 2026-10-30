package com.otboo.team03.user.integration;

import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.entity.UserRole;
import com.otboo.team03.user.init.AdminInitializer;
import com.otboo.team03.user.init.AdminProperties;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AdminInitializerIntegrationTest {

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
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminInitializer adminInitializer;

    @Autowired
    private AdminProperties adminProperties;

    @Test
    void applicationStartup_createsAdmin() {
        // when
        // ApplicationRunner는 Spring Boot 시작 시 이미 실행됨
        User admin = userRepository.findByEmail(adminProperties.email()).orElseThrow();

        // then
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.isLocked()).isFalse();
        assertThat(admin.getName()).isEqualTo(adminProperties.name());
    }

    @Test
    void applicationStartup_encodesAdminPassword() {
        // given
        User admin = userRepository.findByEmail(adminProperties.email()).orElseThrow();

        // then
        assertThat(admin.getPassword()).isNotEqualTo(adminProperties.password());
        assertThat(passwordEncoder.matches(adminProperties.password(), admin.getPassword())).isTrue();
    }

    @Test
    void initialize_adminAlreadyExists_doesNotCreateDuplicate() {
        // given
        long beforeCount = userRepository.count();

        // when
        adminInitializer.initialize();

        // then
        assertThat(userRepository.count()).isEqualTo(beforeCount);
    }
}
