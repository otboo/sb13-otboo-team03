package com.otboo.team03.user.service;

import com.otboo.team03.user.dto.request.UserCreateRequest;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.entity.UserRole;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;


@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("회원가입 성공")
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
}