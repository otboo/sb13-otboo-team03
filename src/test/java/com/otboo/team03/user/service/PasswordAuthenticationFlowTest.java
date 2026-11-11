package com.otboo.team03.user.service;

import com.otboo.team03.global.security.OtbooUserDetailsService;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.RedisTemporaryPasswordRepository;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordAuthenticationFlowTest {

    private static final String EMAIL = "user@example.com";
    private static final String ORIGINAL_PASSWORD = "originalPassword123!";
    private static final String TEMPORARY_PASSWORD = "temporaryPassword123!";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisTemporaryPasswordRepository temporaryPasswordRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private DaoAuthenticationProvider authenticationProvider;

    @BeforeEach
    void setUp() {
        OtbooUserDetailsService userDetailsService = new OtbooUserDetailsService(
                userRepository, temporaryPasswordRepository
        );

        authenticationProvider = new DaoAuthenticationProvider(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);
    }

    @Test
    @DisplayName("임시 비밀번호가 존재해도 기존 비밀번호로 로그인할 수 있다")
    void authenticate_originalPassword_succeedsWithTemporaryPassword() {
        // given
        givenUserWithTemporaryPassword();

        // when
        Authentication result = authenticate(ORIGINAL_PASSWORD);

        // then
        assertThat(result.isAuthenticated()).isTrue();
    }

    @Test
    @DisplayName("임시 비밀번호는 최초 로그인에만 사용할 수 있다")
    void authenticate_temporaryPassword_cannotBeReused() {
        // given
        givenUserWithTemporaryPassword();

        // when
        Authentication result = authenticate(TEMPORARY_PASSWORD);

        // then
        assertThat(result.isAuthenticated()).isTrue();

        assertThatThrownBy(() -> authenticate(TEMPORARY_PASSWORD))
                .isInstanceOf(BadCredentialsException.class);
    }

    private void givenUserWithTemporaryPassword() {
        User user = new User(
                EMAIL,
                passwordEncoder.encode(ORIGINAL_PASSWORD),
                "테스트 사용자"
        );

        String encodedTemporaryPassword = passwordEncoder.encode(TEMPORARY_PASSWORD);

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(temporaryPasswordRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(encodedTemporaryPassword));
    }

    private Authentication authenticate(String password) {
        return authenticationProvider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(EMAIL, password)
        );
    }
}
