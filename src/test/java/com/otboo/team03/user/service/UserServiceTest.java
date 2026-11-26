package com.otboo.team03.user.service;

import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.notification.event.NotificationEvent;
import com.otboo.team03.notification.event.NotificationEventType;
import com.otboo.team03.notification.outbox.service.OutboxEventPublisher;
import com.otboo.team03.profile.repository.ProfileRepository;
import com.otboo.team03.user.dto.request.UserCreateRequest;
import com.otboo.team03.user.dto.request.UserListRequest;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.entity.UserRole;
import com.otboo.team03.user.exception.DuplicateEmailException;
import com.otboo.team03.user.exception.LastAdminException;
import com.otboo.team03.user.exception.UserNotFoundException;
import com.otboo.team03.user.repository.RedisTemporaryPasswordRepository;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.UUID;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtRegistry jwtRegistry;

    @Mock
    private RedisTemporaryPasswordRepository temporaryPasswordRepository;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    private UserService userService;

    @Nested
    @DisplayName("회원가입")
    class CreateUserTest {

        @Test
        @DisplayName("성공 - 회원가입 시 사용자와 기본 프로필 생성")
        void createUser_success() {
            // given
            UserCreateRequest request = new UserCreateRequest(
                    "홍길동",
                    "test@example.com",
                    "password123!"
            );

            given(userRepository.existsByEmail(request.email()))
                    .willReturn(false);

            given(passwordEncoder.encode(request.password()))
                    .willReturn("encodedPassword");

            given(userRepository.save(any(User.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // when
            UserDto result = userService.createUser(request);

            // then
            assertThat(result.email()).isEqualTo(request.email());
            assertThat(result.name()).isEqualTo(request.name());
            assertThat(result.role()).isEqualTo(UserRole.USER);
            assertThat(result.locked()).isFalse();

            then(userRepository).should().save(any(User.class));
            then(profileRepository).should().save(argThat(profile ->
                    profile.getUser() != null
                            && profile.getUser().getEmail().equals(request.email())
            ));
        }

        @Test
        @DisplayName("실패 - 이메일 중복")
        void createUser_duplicateEmail() {
            // given
            UserCreateRequest request = new UserCreateRequest(
                    "홍길동",
                    "test@example.com",
                    "password123!"
            );

            given(userRepository.existsByEmail(request.email()))
                    .willReturn(true);

            // when & then
            assertThatThrownBy(() -> userService.createUser(request))
                    .isInstanceOf(DuplicateEmailException.class);

            then(userRepository).should(never()).save(any(User.class));
            then(passwordEncoder).should(never()).encode(anyString());
        }
    }

    @Nested
    @DisplayName("사용자 권한 변경")
    class UpdateRoleTest {

        @BeforeEach
        void setUp() {
            TransactionSynchronizationManager.initSynchronization();
            setTestAuthentication();
        }

        @AfterEach
        void tearDown() {
            TransactionSynchronizationManager.clearSynchronization();
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("성공 - USER 권한을 ADMIN으로 변경하고 UserDto 반환")
        void updateRole_userToAdmin_success() {
            // given
            UUID userId = UUID.randomUUID();
            User user = new User("user@example.com", "encodedPassword", "일반 사용자");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            UserDto result = userService.updateRole(userId, UserRole.ADMIN);

            // then
            assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
            assertThat(result.role()).isEqualTo(UserRole.ADMIN);
            assertThat(result.email()).isEqualTo(user.getEmail());
            assertThat(result.name()).isEqualTo(user.getName());
            verify(userRepository).findById(userId);
            verify(jwtRegistry, never()).invalidateJwtInformationByUserId(userId);
            verify(outboxEventPublisher).publish(
                    argThat(event -> event.eventType() == NotificationEventType.USER_ROLE_CHANGED
                            && event.aggregateId().equals(userId)
                            && event.receiverId().equals(userId)
                            && event.actorId() != null
                            && event.relatedId().equals(userId)),
                    eq("USER"),
                    eq(userId.toString())
            );
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 사용자 권한 변경 시 예외 발생")
        void updateRole_userNotFound_throwsException() {
            // given
            UUID userId = UUID.randomUUID();

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.updateRole(userId, UserRole.ADMIN))
                    .isInstanceOf(UserNotFoundException.class);

            verify(userRepository).findById(userId);
        }

        @Test
        @DisplayName("성공 - 동일한 권한 요청 시 기존 권한과 JWT 세션 유지")
        void updateRole_sameRole_doesNotChangeRole() {
            // given
            UUID userId = UUID.randomUUID();
            User user = new User("user@example.com", "encodedPassword", "일반 사용자");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            UserDto result = userService.updateRole(userId, UserRole.USER);

            // then
            assertThat(user.getRole()).isEqualTo(UserRole.USER);
            assertThat(result.role()).isEqualTo(UserRole.USER);
            verify(userRepository).findById(userId);
            verify(userRepository, never()).save(any(User.class));
            verify(jwtRegistry, never()).invalidateJwtInformationByUserId(userId);
            verify(outboxEventPublisher, never()).publish(
                    any(NotificationEvent.class), anyString(), anyString()
            );
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        }

        @Test
        @DisplayName("실패 - 마지막 ADMIN을 USER로 변경하면 예외 발생")
        void updateRole_lastAdmin_throwsException() {
            // given
            UUID userId = UUID.randomUUID();
            User admin = new User("admin@example.com", "encodedPassword", "관리자");
            admin.changeRole(UserRole.ADMIN);

            when(userRepository.findById(userId)).thenReturn(Optional.of(admin));
            when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(1L);

            // when & then
            assertThatThrownBy(() -> userService.updateRole(userId, UserRole.USER))
                    .isInstanceOf(LastAdminException.class);

            assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        }

        @Test
        @DisplayName("실패 - 다른 ADMIN이 잠겨 있다면 마지막 활성 ADMIN 강등을 차단한다")
        void updateRole_lastActiveAdmin_throwsException() {
            // given
            UUID userId = UUID.randomUUID();
            User admin = new User("admin@example.com", "encodedPassword", "관리자");
            admin.changeRole(UserRole.ADMIN);

            when(userRepository.findById(userId)).thenReturn(Optional.of(admin));
            when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(2L);
            when(userRepository.countByRoleAndLockedFalse(UserRole.ADMIN)).thenReturn(1L);

            // when & then
            assertThatThrownBy(() -> userService.updateRole(userId, UserRole.USER))
                    .isInstanceOf(LastAdminException.class);

            assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        }

        @Test
        @DisplayName("성공 - DB 커밋 후 기존 JWT 세션 무효화")
        void updateRole_roleChanged_invalidatesJwtSession() {
            // given
            UUID userId = UUID.randomUUID();
            User user = new User("user@example.com", "encodedPassword", "일반 사용자");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            UserDto result = userService.updateRole(userId, UserRole.ADMIN);

            // then: 권한 변경 직후 Redis 무효화 미실행
            assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
            assertThat(result.role()).isEqualTo(UserRole.ADMIN);
            verify(jwtRegistry, never()).invalidateJwtInformationByUserId(userId);

            // when: DB 커밋 콜백 실행
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            // then: 커밋 후 Redis 무효화 실행
            verify(jwtRegistry).invalidateJwtInformationByUserId(userId);
        }

        @Test
        @DisplayName("성공 - DB 롤백 시 기존 JWT 세션 유지")
        void updateRole_roleChanged_rollbackKeepsJwtSession() {
            // given
            UUID userId = UUID.randomUUID();
            User user = new User("user@example.com", "encodedPassword", "일반 사용자");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            userService.updateRole(userId, UserRole.ADMIN);

            // then: 커밋 전 Redis 무효화 미실행
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
            verify(jwtRegistry, never()).invalidateJwtInformationByUserId(userId);

            // when: DB 롤백 콜백 실행
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            // then: Redis 세션 유지
            verify(jwtRegistry, never()).invalidateJwtInformationByUserId(userId);
        }

        @Test
        @DisplayName("Redis 세션 무효화 실패 시 커밋 후 예외를 전파하지 않는다")
        void updateRole_afterCommit_redisFailureDoesNotPropagate() {
            // given
            UUID userId = UUID.randomUUID();
            User user = new User("user@example.com", "encodedPassword", "일반 사용자");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            doThrow(new RedisConnectionFailureException("Redis 연결 실패"))
                    .when(jwtRegistry).invalidateJwtInformationByUserId(userId);

            // when
            UserDto result = userService.updateRole(userId, UserRole.ADMIN);

            // then
            assertThat(result.role()).isEqualTo(UserRole.ADMIN);
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

            assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit))
                    .doesNotThrowAnyException();

            verify(jwtRegistry).invalidateJwtInformationByUserId(userId);
        }
    }

    @Nested
    @DisplayName("사용자 목록 조회")
    class GetUserListTest {

        @Test
        @DisplayName("커서 페이지네이션 결과 및 메타데이터 반환")
        void shouldReturnUserListWithCursorPaginationMetadata() {
            // given
            UserListRequest request = new UserListRequest(
                    null, null, 2, "email", "ASCENDING", null, null, null
            );

            User alice = mock(User.class);
            User bob = mock(User.class);
            User charlie = mock(User.class);

            UUID aliceId = UUID.randomUUID();
            UUID bobId = UUID.randomUUID();

            when(alice.getId()).thenReturn(aliceId);
            when(alice.getEmail()).thenReturn("alice@example.com");
            when(alice.getName()).thenReturn("Alice");
            when(alice.getRole()).thenReturn(UserRole.USER);
            when(alice.getCreatedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));

            when(bob.getId()).thenReturn(bobId);
            when(bob.getEmail()).thenReturn("bob@example.com");
            when(bob.getName()).thenReturn("Bob");
            when(bob.getRole()).thenReturn(UserRole.USER);
            when(bob.getCreatedAt()).thenReturn(Instant.parse("2026-01-02T00:00:00Z"));

            when(userRepository.findUsersByCursor(request)).thenReturn(List.of(alice, bob, charlie));
            when(userRepository.countUsers(request)).thenReturn(5L);

            // when
            CursorResponse<UserDto> response = userService.getUsers(request);

            // then
            assertThat(response.data())
                    .hasSize(2)
                    .extracting(UserDto::email)
                    .containsExactly("alice@example.com", "bob@example.com");

            assertThat(response.hasNext()).isTrue();
            assertThat(response.nextCursor()).isEqualTo("bob@example.com");
            assertThat(response.nextIdAfter()).isEqualTo(bobId);
            assertThat(response.totalCount()).isEqualTo(5L);
            assertThat(response.sortBy()).isEqualTo("email");
            assertThat(response.sortDirection()).isEqualTo("ASCENDING");

            verify(userRepository).findUsersByCursor(request);
            verify(userRepository).countUsers(request);
        }
    }

    @Nested
    @DisplayName("비밀번호 변경")
    class ChangePasswordTest {

        @BeforeEach
        void setUp() {
            TransactionSynchronizationManager.initSynchronization();
        }

        @AfterEach
        void tearDown() {
            TransactionSynchronizationManager.clearSynchronization();
        }

        @Test
        @DisplayName("성공 - 새 비밀번호를 암호화하여 변경한다")
        void changePassword_success() {
            // given
            UUID userId = UUID.randomUUID();
            String newPassword = "newPassword123!";
            User user = new User("user@example.com", "encodedOldPassword", "일반 사용자");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(newPassword)).thenReturn("encodedNewPassword");

            // when
            userService.changePassword(userId, newPassword);

            // then
            assertThat(user.getPassword()).isEqualTo("encodedNewPassword");
            verify(passwordEncoder).encode(newPassword);
            verify(userRepository).findById(userId);
            verify(outboxEventPublisher, never()).publish(
                    any(NotificationEvent.class), anyString(), anyString()
            );
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 사용자의 비밀번호 변경 시 예외 발생")
        void changePassword_userNotFound_throwsException() {
            // given
            UUID userId = UUID.randomUUID();

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.changePassword(userId, "newPassword123!"))
                    .isInstanceOf(UserNotFoundException.class);

            verify(passwordEncoder, never()).encode(anyString());
            verifyNoInteractions(temporaryPasswordRepository, jwtRegistry);
        }

        @Test
        @DisplayName("성공 - DB 커밋 후 임시 비밀번호와 기존 JWT 세션을 무효화한다")
        void changePassword_afterCommit_invalidatesCredentials() {
            // given
            UUID userId = UUID.randomUUID();
            String email = "user@example.com";
            User user = new User(email, "encodedOldPassword", "일반 사용자");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("newPassword123!")).thenReturn("encodedNewPassword");

            // when
            userService.changePassword(userId, "newPassword123!");

            // then: DB 커밋 전에는 무효화하지 않음
            verify(temporaryPasswordRepository, never()).delete(email);
            verify(jwtRegistry, never()).invalidateJwtInformationByUserId(userId);

            // when: DB 커밋 콜백 실행
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            // then: 커밋 이후 무효화
            verify(temporaryPasswordRepository).delete(email);
            verify(jwtRegistry).invalidateJwtInformationByUserId(userId);
        }

        @Test
        @DisplayName("성공 - DB 롤백 시 임시 비밀번호와 JWT 세션을 유지한다")
        void changePassword_rollback_keepsCredentials() {
            // given
            UUID userId = UUID.randomUUID();
            String email = "user@example.com";
            User user = new User(email, "encodedOldPassword", "일반 사용자");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("newPassword123!")).thenReturn("encodedNewPassword");

            // when
            userService.changePassword(userId, "newPassword123!");

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            // then
            verify(temporaryPasswordRepository, never()).delete(email);
            verify(jwtRegistry, never()).invalidateJwtInformationByUserId(userId);
        }

        @Test
        @DisplayName("Redis 세션 무효화 실패 시 커밋 후 예외를 전파하지 않는다")
        void changePassword_afterCommit_redisFailureDoesNotPropagate() {
            // given
            UUID userId = UUID.randomUUID();
            User user = new User("user@example.com", "encodedOldPassword", "일반 사용자");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("newPassword123!")).thenReturn("encodedNewPassword");

            doThrow(new RedisConnectionFailureException("Redis 연결 실패"))
                    .when(jwtRegistry).invalidateJwtInformationByUserId(userId);

            // when
            userService.changePassword(userId, "newPassword123!");

            // then
            assertThat(user.getPassword()).isEqualTo("encodedNewPassword");
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

            assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit))
                    .doesNotThrowAnyException();

            verify(temporaryPasswordRepository).delete(user.getEmail());
            verify(jwtRegistry).invalidateJwtInformationByUserId(userId);
        }

        @Test
        @DisplayName("임시 비밀번호 삭제 실패 시에도 JWT 세션을 무효화한다")
        void changePassword_afterCommit_temporaryPasswordDeleteFailure() {
            // given
            UUID userId = UUID.randomUUID();
            User user = new User("user@example.com", "encodedOldPassword", "일반 사용자");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("newPassword123!")).thenReturn("encodedNewPassword");

            doThrow(new RedisConnectionFailureException("Redis 연결 실패"))
                    .when(temporaryPasswordRepository).delete(user.getEmail());

            // when
            userService.changePassword(userId, "newPassword123!");

            // then
            assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit))
                    .doesNotThrowAnyException();

            verify(temporaryPasswordRepository).delete(user.getEmail());
            verify(jwtRegistry).invalidateJwtInformationByUserId(userId);
        }
    }

    @Nested
    @DisplayName("계정 잠금, 해제")
    class UpdateLockTest {

        @BeforeEach
        void setUp() {
            TransactionSynchronizationManager.initSynchronization();
            setTestAuthentication();
        }

        @AfterEach
        void tearDown() {
            TransactionSynchronizationManager.clearSynchronization();
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("성공 - 계정을 잠그고 변경된 사용자 정보를 반환한다")
        void updateLock_lock_success() {
            // given
            UUID userId = UUID.randomUUID();
            User user = new User("user@example.com", "encodedPassword", "일반 사용자");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            UserDto result = userService.updateLock(userId, true);

            // then
            assertThat(user.isLocked()).isTrue();
            assertThat(result.locked()).isTrue();
            verify(userRepository).findById(userId);
        }

        @Test
        @DisplayName("성공 - 잠긴 계정을 해제한다")
        void updateLock_unlock_success() {
            // given
            UUID userId = UUID.randomUUID();
            User user = new User("user@example.com", "encodedPassword", "일반 사용자");
            user.changeLocked(true);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            UserDto result = userService.updateLock(userId, false);

            // then
            assertThat(user.isLocked()).isFalse();
            assertThat(result.locked()).isFalse();
            verify(jwtRegistry, never()).invalidateJwtInformationByUserId(userId);
        }

        @Test
        @DisplayName("성공 - 동일한 잠금 상태 요청 시 세션 무효화를 실행하지 않는다")
        void updateLock_sameState_doesNotInvalidateSession() {
            // given
            UUID userId = UUID.randomUUID();
            User user = new User("user@example.com", "encodedPassword", "일반 사용자");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            UserDto result = userService.updateLock(userId, false);

            // then
            assertThat(result.locked()).isFalse();
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
            verify(jwtRegistry, never()).invalidateJwtInformationByUserId(userId);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 사용자는 예외가 발생한다")
        void updateLock_userNotFound_throwsException() {
            // given
            UUID userId = UUID.randomUUID();

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.updateLock(userId, true))
                    .isInstanceOf(UserNotFoundException.class);

            verifyNoInteractions(jwtRegistry);
        }

        @Test
        @DisplayName("성공 - DB 커밋 이후 기존 JWT 세션을 무효화한다")
        void updateLock_afterCommit_invalidatesSession() {
            // given
            UUID userId = UUID.randomUUID();
            User user = new User("user@example.com", "encodedPassword", "일반 사용자");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            userService.updateLock(userId, true);

            // then: 커밋 전
            verify(jwtRegistry, never()).invalidateJwtInformationByUserId(userId);
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

            // when: 커밋 완료
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            // then
            verify(jwtRegistry).invalidateJwtInformationByUserId(userId);
        }

        @Test
        @DisplayName("성공 - DB 롤백 시 기존 JWT 세션을 유지한다")
        void updateLock_rollback_keepsSession() {
            // given
            UUID userId = UUID.randomUUID();
            User user = new User("user@example.com", "encodedPassword", "일반 사용자");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            userService.updateLock(userId, true);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization ->
                            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            // then
            verify(jwtRegistry, never()).invalidateJwtInformationByUserId(userId);
        }

        @Test
        @DisplayName("실패 - 마지막 활성 관리자 계정 잠금 시 예외 발생")
        void updateLock_lastActiveAdmin_throwsException() {
            // given
            UUID userId = UUID.randomUUID();
            User admin = new User("admin@example.com", "encodedPassword", "관리자");
            admin.changeRole(UserRole.ADMIN);

            when(userRepository.findById(userId))
                    .thenReturn(Optional.of(admin));

            when(userRepository.countByRoleAndLockedFalse(UserRole.ADMIN))
                    .thenReturn(1L);

            // when & then
            assertThatThrownBy(() -> userService.updateLock(userId, true))
                    .isInstanceOf(LastAdminException.class);

            assertThat(admin.isLocked()).isFalse();
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();

            verify(jwtRegistry, never()).invalidateJwtInformationByUserId(userId);
        }

        @Test
        @DisplayName("Redis 세션 무효화 실패 시 커밋 후 예외를 전파하지 않는다")
        void updateLock_afterCommit_redisFailureDoesNotPropagate() {
            // given
            UUID userId = UUID.randomUUID();
            User user = new User("user@example.com", "encodedPassword", "일반 사용자");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            doThrow(new RedisConnectionFailureException("Redis 연결 실패"))
                    .when(jwtRegistry).invalidateJwtInformationByUserId(userId);

            // when
            UserDto result = userService.updateLock(userId, true);

            // then
            assertThat(result.locked()).isTrue();
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

            assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit))
                    .doesNotThrowAnyException();

            verify(jwtRegistry).invalidateJwtInformationByUserId(userId);
        }
    }

    private void setTestAuthentication() {
        UserDto userDto = new UserDto(
                UUID.randomUUID(),
                Instant.now(),
                "admin@example.com",
                "관리자",
                UserRole.ADMIN,
                false
        );

        OtbooUserDetails principal = new OtbooUserDetails(userDto, "encodedPassword");

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
