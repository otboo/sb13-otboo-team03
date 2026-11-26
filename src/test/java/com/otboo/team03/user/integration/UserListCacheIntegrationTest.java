
package com.otboo.team03.user.integration;

import com.otboo.team03.Sb13OtbooTeam03Application;
import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.profile.dto.ProfileUpdateRequest;
import com.otboo.team03.profile.repository.ProfileRepository;
import com.otboo.team03.profile.service.ProfileService;
import com.otboo.team03.user.dto.request.UserCreateRequest;
import com.otboo.team03.user.dto.request.UserListRequest;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.entity.UserRole;
import com.otboo.team03.user.init.AdminProperties;
import com.otboo.team03.user.repository.UserRepository;
import com.otboo.team03.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = Sb13OtbooTeam03Application.class)
@ActiveProfiles("test")
@Testcontainers
class UserListCacheIntegrationTest {

    private static final String CACHE_NAME = "user-list";

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
    private UserService userService;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private AdminProperties adminProperties;

    @Autowired
    private CacheManager cacheManager;

    private final List<UUID> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        getCache().clear();

        User admin = userRepository.findByEmail(adminProperties.email()).orElseThrow();
        OtbooUserDetails principal = new OtbooUserDetails(UserDto.from(admin), admin.getPassword());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();

        if (!createdUserIds.isEmpty()) {
            profileRepository.deleteAllById(createdUserIds);
            userRepository.deleteAllById(createdUserIds);
        }

        getCache().clear();
    }

    @Test
    @DisplayName("필터 없는 첫 페이지를 재조회하면 캐시된 결과를 반환한다")
    void get_defaultFirstPage_returnsCachedValue() {
        // given
        UserListRequest request = defaultRequest();

        // when
        CursorResponse<UserDto> first = userService.getUsers(request);

        // then
        Cache cache = getCache();
        assertThat(cache.get(request)).isNotNull();
        assertThat(cache.get(request).get()).isEqualTo(first);

        // when
        CursorResponse<UserDto> second = userService.getUsers(request);

        // then
        assertThat(second).isEqualTo(first);

        // 다른 정렬 조건은 별도 캐시 키 사용
        UserListRequest anotherRequest = new UserListRequest(
                null, null, 10, "createdAt", "DESCENDING", null, null, null
        );

        assertThat(cache.get(anotherRequest)).isNull();

        userService.getUsers(anotherRequest);

        assertThat(cache.get(anotherRequest)).isNotNull();
    }

    @Test
    @DisplayName("검색, 필터 및 다음 페이지 요청은 캐싱하지 않는다")
    void get_filteredOrNextPage_doesNotCache() {
        // given
        List<UserListRequest> requests = List.of(
                new UserListRequest(null, null, 10, "email", "ASCENDING", "test", null, null),
                new UserListRequest(null, null, 10, "email", "ASCENDING", null, UserRole.ADMIN, null),
                new UserListRequest(null, null, 10, "email", "ASCENDING", null, null, false),
                new UserListRequest("abc", null, 10, "email", "ASCENDING", null, null, null)
        );

        // when & then
        for (UserListRequest request : requests) {
            userService.getUsers(request);
            assertThat(getCache().get(request)).isNull();
        }
    }

    @Test
    @DisplayName("사용자 생성 후 기본 목록 캐시가 무효화된다")
    void createUser_invalidatesUserListCache() {
        // given
        UserListRequest request = defaultRequest();
        getCache().put(request, "cached");

        String email = "cache-" + UUID.randomUUID() + "@example.com";
        UserCreateRequest createRequest = new UserCreateRequest("테스트 사용자", email, "password123!");

        // when
        UserDto created = userService.createUser(createRequest);
        createdUserIds.add(created.id());

        // then
        assertThat(getCache().get(request)).isNull();
    }

    @Test
    @DisplayName("권한 변경과 계정 잠금 후 기본 목록 캐시가 무효화된다")
    void updateRoleAndLock_invalidatesUserListCache() {
        // given
        User roleTarget = createTestUser();
        User lockTarget = createTestUser();
        UserListRequest request = defaultRequest();

        // when: 권한 변경
        getCache().put(request, "cached");
        userService.updateRole(roleTarget.getId(), UserRole.ADMIN);

        // then
        assertThat(getCache().get(request)).isNull();

        // when: 계정 잠금
        getCache().put(request, "cached");
        userService.updateLock(lockTarget.getId(), true);

        // then
        assertThat(getCache().get(request)).isNull();
    }

    @Test
    @DisplayName("프로필에서 이름 변경 시에만 기본 목록 캐시가 무효화된다")
    void updateProfileName_invalidatesUserListCache() {
        // given
        String email = "cache-" + UUID.randomUUID() + "@example.com";
        UserDto user = userService.createUser(
                new UserCreateRequest("기존 이름", email, "password123!")
        );
        createdUserIds.add(user.id());

        UserListRequest listRequest = defaultRequest();
        getCache().put(listRequest, "cached");

        // when: 이름을 변경하지 않음
        profileService.updateProfile(
                user.id(),
                new ProfileUpdateRequest(null, null, null, null, null),
                null
        );

        // then
        assertThat(getCache().get(listRequest)).isNotNull();

        // when: 이름 변경
        profileService.updateProfile(
                user.id(),
                new ProfileUpdateRequest("변경된 이름", null, null, null, null),
                null
        );

        // then
        assertThat(getCache().get(listRequest)).isNull();
    }

    @Test
    @DisplayName("캐시된 사용자 목록도 ADMIN 권한이 없으면 조회할 수 없다")
    void get_cachedUserList_deniesNonAdmin() {
        // given: ADMIN이 먼저 캐시 생성
        UserListRequest request = defaultRequest();
        userService.getUsers(request);
        assertThat(getCache().get(request)).isNotNull();

        // when: USER 권한으로 변경
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "regular-user",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        // then
        assertThatThrownBy(() -> userService.getUsers(request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("limit 값이 다르면 별도의 캐시 키를 사용한다")
    void get_differentLimits_usesSeparateCacheKeys() {
        // given
        UserListRequest limit10 = defaultRequest();
        UserListRequest limit20 = new UserListRequest(
                null, null, 20, "email", "ASCENDING", null, null, null
        );
        Cache cache = getCache();

        // when: limit 10 조회
        userService.getUsers(limit10);

        // then
        assertThat(cache.get(limit10)).isNotNull();
        assertThat(cache.get(limit20)).isNull();

        // when: limit 20 조회
        userService.getUsers(limit20);

        // then
        assertThat(cache.get(limit10)).isNotNull();
        assertThat(cache.get(limit20)).isNotNull();
    }

    private Cache getCache() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();
        return cache;
    }

    private UserListRequest defaultRequest() {
        return new UserListRequest(
                null, null, 10, "email", "ASCENDING", null, null, null
        );
    }

    private User createTestUser() {
        User user = userRepository.saveAndFlush(
                new User("cache-" + UUID.randomUUID() + "@example.com", "encodedPassword", "테스트 사용자")
        );
        createdUserIds.add(user.getId());
        return user;
    }
}
