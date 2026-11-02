package com.otboo.team03.user.service;

import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.user.dto.request.UserCreateRequest;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.entity.UserRole;
import com.otboo.team03.user.exception.DuplicateEmailException;
import com.otboo.team03.user.exception.UserNotFoundException;
import com.otboo.team03.user.repository.RedisTemporaryPasswordRepository;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;

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
    private RedisTemporaryPasswordRepository temporaryPasswordRepository;

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
    }

}