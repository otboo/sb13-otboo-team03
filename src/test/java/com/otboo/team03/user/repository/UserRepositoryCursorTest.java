package com.otboo.team03.user.repository;

import com.otboo.team03.user.dto.request.UserListRequest;
import com.otboo.team03.user.entity.User;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.otboo.team03.user.init.AdminInitializer;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.internal.jdbc.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class UserRepositoryCursorTest {

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
    private EntityManager entityManager;

    @MockitoBean
    private AdminInitializer adminInitializer;

    @Test
    void shouldFindUsersSortedByEmailAscendingWithLimitPlusOne() {
        // given
        userRepository.save(new User("a-charlie@example.com", "encodedPassword", "Charlie"));
        userRepository.save(new User("a-alice@example.com", "encodedPassword", "Alice"));
        userRepository.save(new User("a-bob@example.com", "encodedPassword", "Bob"));

        UserListRequest request = new UserListRequest(
                null, null, 2, "email", "ASCENDING", null, null, null
        );

        // when
        List<User> users = userRepository.findUsersByCursor(request);

        // then
        assertThat(users)
                .extracting(User::getEmail)
                .containsExactly(
                        "a-alice@example.com",
                        "a-bob@example.com",
                        "a-charlie@example.com"
                );
    }

    @Test
    void shouldFindUsersSortedByEmailDescending() {
        // given
        userRepository.save(new User("a-charlie@example.com", "encodedPassword", "Charlie"));
        userRepository.save(new User("a-alice@example.com", "encodedPassword", "Alice"));
        userRepository.save(new User("a-bob@example.com", "encodedPassword", "Bob"));

        UserListRequest request = new UserListRequest(
                null, null, 2, "email", "DESCENDING", null, null, null
        );

        // when
        List<User> users = userRepository.findUsersByCursor(request);

        // then
        assertThat(users)
                .extracting(User::getEmail)
                .containsExactly(
                        "a-charlie@example.com",
                        "a-bob@example.com",
                        "a-alice@example.com"
                );
    }

    @Test
    void shouldFindUsersSortedByCreatedAtAscending() {
        // given
        User first = userRepository.saveAndFlush(new User("z-first@example.com", "encodedPassword", "First"));
        User second = userRepository.saveAndFlush(new User("x-second@example.com", "encodedPassword", "Second"));
        User third = userRepository.saveAndFlush(new User("y-third@example.com", "encodedPassword", "Third"));

        updateCreatedAt(first.getId(), Instant.parse("2026-01-01T00:00:00Z"));
        updateCreatedAt(second.getId(), Instant.parse("2026-02-01T00:00:00Z"));
        updateCreatedAt(third.getId(), Instant.parse("2026-03-01T00:00:00Z"));
        entityManager.clear();

        UserListRequest request = new UserListRequest(
                null, null, 2, "createdAt", "ASCENDING", null, null, null
        );

        // when
        List<User> users = userRepository.findUsersByCursor(request);

        // then
        assertThat(users)
                .extracting(User::getId)
                .containsExactly(first.getId(), second.getId(), third.getId());
    }

    @Test
    void shouldFindUsersSortedByCreatedAtDescending() {
        // given
        User first = userRepository.saveAndFlush(new User("z-first@example.com", "encodedPassword", "First"));
        User second = userRepository.saveAndFlush(new User("x-second@example.com", "encodedPassword", "Second"));
        User third = userRepository.saveAndFlush(new User("y-third@example.com", "encodedPassword", "Third"));

        updateCreatedAt(first.getId(), Instant.parse("2026-01-01T00:00:00Z"));
        updateCreatedAt(second.getId(), Instant.parse("2026-02-01T00:00:00Z"));
        updateCreatedAt(third.getId(), Instant.parse("2026-03-01T00:00:00Z"));
        entityManager.clear();

        UserListRequest request = new UserListRequest(
                null, null, 2, "createdAt", "DESCENDING", null, null, null
        );

        // when
        List<User> users = userRepository.findUsersByCursor(request);

        // then
        assertThat(users)
                .extracting(User::getId)
                .containsExactly(third.getId(), second.getId(), first.getId());
    }

    private void updateCreatedAt(UUID userId, Instant createdAt) {
        entityManager.createQuery("UPDATE User u SET u.createdAt = :createdAt WHERE u.id = :userId")
                .setParameter("createdAt", createdAt)
                .setParameter("userId", userId)
                .executeUpdate();
    }


}