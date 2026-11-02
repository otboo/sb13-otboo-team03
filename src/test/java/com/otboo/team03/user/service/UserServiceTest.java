
package com.otboo.team03.user.service;

import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.user.dto.request.UserCreateRequest;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.entity.UserRole;
import com.otboo.team03.user.exception.DuplicateEmailException;
import com.otboo.team03.user.exception.LastAdminException;
import com.otboo.team03.user.exception.UserNotFoundException;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtRegistry jwtRegistry;

    @InjectMocks
    private UserService userService;

    @Nested
    @DisplayName("회원가입")
    class CreateUserTest {

        @Test
        @DisplayName("성공")
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
        }

        @AfterEach
        void tearDown() {
            TransactionSynchronizationManager.clearSynchronization();
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
    }
}
