package com.otboo.team03.user.service;

import com.otboo.team03.user.dto.request.UserCreateRequest;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.entity.UserRole;
import com.otboo.team03.user.exception.DuplicateEmailException;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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

    @Test
    void updateRole_userToAdmin_success() {
        // given
        UUID userId = UUID.randomUUID();
        User user = new User("user@example.com", "encodedPassword", "일반 사용자");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        userService.updateRole(userId, UserRole.ADMIN);

        // then
        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
        verify(userRepository).findById(userId);
    }
}