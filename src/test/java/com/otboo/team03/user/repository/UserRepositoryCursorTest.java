package com.otboo.team03.user.repository;

import com.otboo.team03.user.dto.request.UserListRequest;
import com.otboo.team03.user.entity.User;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.otboo.team03.user.entity.UserRole;
import com.otboo.team03.user.init.AdminInitializer;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.internal.jdbc.JdbcTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @Nested
    @DisplayName("사용자 목록 정렬")
    class SortTest {

        @Test
        @DisplayName("이메일 오름차순 정렬 및 limit + 1건 조회")
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
        @DisplayName("이메일 내림차순 정렬")
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
        @DisplayName("생성일 오름차순 정렬")
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
        @DisplayName("생성일 내림차순 정렬")
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
    }

    @Nested
    @DisplayName("사용자 목록 필터링")
    class FilterTest {

        @Test
        @DisplayName("이메일 부분 일치 필터링")
        void shouldFilterUsersByEmailLike() {
            // given
            userRepository.save(new User("alice@filter-test.com", "encodedPassword", "Alice"));
            userRepository.save(new User("bob@filter-test.com", "encodedPassword", "Bob"));
            userRepository.save(new User("charlie@other-test.com", "encodedPassword", "Charlie"));

            UserListRequest request = new UserListRequest(
                    null, null, 10, "email", "ASCENDING", "filter-test.com", null, null
            );

            // when
            List<User> users = userRepository.findUsersByCursor(request);

            // then
            assertThat(users)
                    .extracting(User::getEmail)
                    .containsExactly(
                            "alice@filter-test.com",
                            "bob@filter-test.com"
                    );
        }

        @Test
        @DisplayName("사용자 권한 필터링")
        void shouldFilterUsersByRole() {
            // given
            User admin = new User("admin@filter-test.com", "encodedPassword", "Admin");
            admin.changeRole(UserRole.ADMIN);

            userRepository.save(admin);
            userRepository.save(new User("user@filter-test.com", "encodedPassword", "User"));

            UserListRequest request = new UserListRequest(
                    null, null, 10, "email", "ASCENDING", null, UserRole.ADMIN, null
            );

            // when
            List<User> users = userRepository.findUsersByCursor(request);

            // then
            assertThat(users)
                    .extracting(User::getEmail)
                    .containsExactly("admin@filter-test.com");
        }

        @Test
        @DisplayName("계정 잠금 상태 필터링")
        void shouldFilterUsersByLockedStatus() {
            // given
            User lockedUser = userRepository.saveAndFlush(
                    new User("locked@filter-test.com", "encodedPassword", "Locked")
            );

            userRepository.save(new User("unlocked@filter-test.com", "encodedPassword", "Unlocked"));

            entityManager.createQuery("UPDATE User u SET u.locked = true WHERE u.id = :userId")
                    .setParameter("userId", lockedUser.getId())
                    .executeUpdate();

            entityManager.clear();

            UserListRequest request = new UserListRequest(
                    null, null, 10, "email", "ASCENDING", null, null, true
            );

            // when
            List<User> users = userRepository.findUsersByCursor(request);

            // then
            assertThat(users)
                    .extracting(User::getEmail)
                    .containsExactly("locked@filter-test.com");
        }
    }

    @Nested
    @DisplayName("사용자 목록 커서 페이지네이션")
    class CursorPaginationTest {

        @Test
        @DisplayName("이메일 오름차순 커서 기반 다음 페이지 조회")
        void shouldFindNextPageByEmailAscendingCursor() {
            // given
            userRepository.save(new User("a-alice@example.com", "encodedPassword", "Alice"));
            User bob = userRepository.save(new User("a-bob@example.com", "encodedPassword", "Bob"));
            userRepository.save(new User("a-charlie@example.com", "encodedPassword", "Charlie"));
            userRepository.save(new User("a-david@example.com", "encodedPassword", "David"));
            userRepository.save(new User("a-eric@example.com", "encodedPassword", "Eric"));

            UserListRequest request = new UserListRequest(
                    bob.getEmail(), bob.getId(), 2, "email", "ASCENDING", null, null, null
            );

            // when
            List<User> users = userRepository.findUsersByCursor(request);

            // then
            assertThat(users)
                    .extracting(User::getEmail)
                    .containsExactly(
                            "a-charlie@example.com",
                            "a-david@example.com",
                            "a-eric@example.com"
                    );
        }

        @Test
        @DisplayName("이메일 내림차순 커서 기반 다음 페이지 조회")
        void shouldFindNextPageByEmailDescendingCursor() {
            // given
            userRepository.save(new User("a-alice@example.com", "encodedPassword", "Alice"));
            userRepository.save(new User("a-bob@example.com", "encodedPassword", "Bob"));
            userRepository.save(new User("a-charlie@example.com", "encodedPassword", "Charlie"));
            User david = userRepository.save(new User("a-david@example.com", "encodedPassword", "David"));
            userRepository.save(new User("a-eric@example.com", "encodedPassword", "Eric"));

            UserListRequest request = new UserListRequest(
                    david.getEmail(), david.getId(), 2, "email", "DESCENDING", null, null, null
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
        @DisplayName("생성일 오름차순 커서 기반 다음 페이지 조회")
        void shouldFindNextPageByCreatedAtAscendingCursor() {
            // given
            User first = userRepository.saveAndFlush(new User("first@cursor-test.com", "encodedPassword", "First"));
            User second = userRepository.saveAndFlush(new User("second@cursor-test.com", "encodedPassword", "Second"));
            User third = userRepository.saveAndFlush(new User("third@cursor-test.com", "encodedPassword", "Third"));
            User fourth = userRepository.saveAndFlush(new User("fourth@cursor-test.com", "encodedPassword", "Fourth"));

            updateCreatedAt(first.getId(), Instant.parse("2026-01-01T00:00:00Z"));
            updateCreatedAt(second.getId(), Instant.parse("2026-02-01T00:00:00Z"));
            updateCreatedAt(third.getId(), Instant.parse("2026-03-01T00:00:00Z"));
            updateCreatedAt(fourth.getId(), Instant.parse("2026-04-01T00:00:00Z"));
            entityManager.clear();

            UserListRequest request = new UserListRequest(
                    "2026-02-01T00:00:00Z", second.getId(), 2,
                    "createdAt", "ASCENDING", null, null, null
            );

            // when
            List<User> users = userRepository.findUsersByCursor(request);

            // then
            assertThat(users)
                    .extracting(User::getId)
                    .containsExactly(third.getId(), fourth.getId());
        }

        @Test
        @DisplayName("생성일 내림차순 커서 기반 다음 페이지 조회")
        void shouldFindNextPageByCreatedAtDescendingCursor() {
            // given
            User first = userRepository.saveAndFlush(new User("first-desc@cursor-test.com", "encodedPassword", "First"));
            User second = userRepository.saveAndFlush(new User("second-desc@cursor-test.com", "encodedPassword", "Second"));
            User third = userRepository.saveAndFlush(new User("third-desc@cursor-test.com", "encodedPassword", "Third"));
            User fourth = userRepository.saveAndFlush(new User("fourth-desc@cursor-test.com", "encodedPassword", "Fourth"));

            updateCreatedAt(first.getId(), Instant.parse("2026-01-01T00:00:00Z"));
            updateCreatedAt(second.getId(), Instant.parse("2026-02-01T00:00:00Z"));
            updateCreatedAt(third.getId(), Instant.parse("2026-03-01T00:00:00Z"));
            updateCreatedAt(fourth.getId(), Instant.parse("2026-04-01T00:00:00Z"));
            entityManager.clear();

            UserListRequest request = new UserListRequest(
                    "2026-03-01T00:00:00Z", third.getId(), 2,
                    "createdAt", "DESCENDING", null, null, null
            );

            // when
            List<User> users = userRepository.findUsersByCursor(request);

            // then
            assertThat(users)
                    .extracting(User::getId)
                    .containsExactly(second.getId(), first.getId());
        }

        @Test
        @DisplayName("생성일이 동일한 경우 UUID 보조 커서로 다음 페이지 조회")
        void shouldFindNextPageUsingIdAfterWhenCreatedAtIsSame() {
            // given
            User first = userRepository.saveAndFlush(new User("first-tie@cursor-test.com", "encodedPassword", "First"));
            User second = userRepository.saveAndFlush(new User("second-tie@cursor-test.com", "encodedPassword", "Second"));
            User third = userRepository.saveAndFlush(new User("third-tie@cursor-test.com", "encodedPassword", "Third"));

            Instant sameCreatedAt = Instant.parse("2026-02-01T00:00:00Z");

            updateCreatedAt(first.getId(), sameCreatedAt);
            updateCreatedAt(second.getId(), sameCreatedAt);
            updateCreatedAt(third.getId(), sameCreatedAt);
            entityManager.clear();

            UserListRequest firstRequest = new UserListRequest(
                    null, null, 2, "createdAt", "ASCENDING", null, null, null
            );

            // DB에서 실제 UUID 정렬 순서 확인
            List<User> sortedUsers = userRepository.findUsersByCursor(firstRequest);

            assertThat(sortedUsers).hasSize(3);

            // 첫 번째 사용자를 커서로 지정
            User cursorUser = sortedUsers.get(0);

            UserListRequest request = new UserListRequest(
                    sameCreatedAt.toString(), cursorUser.getId(), 2,
                    "createdAt", "ASCENDING", null, null, null
            );

            // when
            List<User> users = userRepository.findUsersByCursor(request);

            // then
            assertThat(users)
                    .extracting(User::getId)
                    .containsExactly(
                            sortedUsers.get(1).getId(),
                            sortedUsers.get(2).getId()
                    );
        }
    }

    private void updateCreatedAt(UUID userId, Instant createdAt) {
        entityManager.createQuery("UPDATE User u SET u.createdAt = :createdAt WHERE u.id = :userId")
                .setParameter("createdAt", createdAt)
                .setParameter("userId", userId)
                .executeUpdate();
    }


}