package com.otboo.team03.user.service;

import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.user.dto.request.UserCreateRequest;
import com.otboo.team03.user.dto.request.UserListRequest;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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