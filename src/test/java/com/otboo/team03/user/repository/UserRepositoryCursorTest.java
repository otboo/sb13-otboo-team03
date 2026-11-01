package com.otboo.team03.user.repository;

import com.otboo.team03.user.dto.request.UserListRequest;
import com.otboo.team03.user.entity.User;
import java.util.List;

import com.otboo.team03.user.init.AdminInitializer;
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


}