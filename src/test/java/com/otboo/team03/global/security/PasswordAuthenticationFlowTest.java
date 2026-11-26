package com.otboo.team03.global.security;

import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.RedisTemporaryPasswordRepository;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.LockedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;

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

    private PasswordAuthenticationProvider authenticationProvider;

    @BeforeEach
    void setUp() {
        OtbooUserDetailsService userDetailsService = new OtbooUserDetailsService(userRepository);

        authenticationProvider = new PasswordAuthenticationProvider(
                userDetailsService, temporaryPasswordRepository, passwordEncoder
        );
    }

    @Test
    @DisplayName("기존 비밀번호로 로그인하면 임시 비밀번호를 사용하지 않고 인증에 성공한다")
    void authenticate_originalPassword_succeedsWithTemporaryPassword() {
        // given
        givenUser();

        // when
        Authentication result = authenticate(ORIGINAL_PASSWORD);

        // then
        assertThat(result.isAuthenticated()).isTrue();
        verifyNoInteractions(temporaryPasswordRepository);
    }

    @Test
    @DisplayName("임시 비밀번호는 최초 로그인에만 사용할 수 있다")
    void authenticate_temporaryPassword_cannotBeReused() {
        // given
        givenUser();

        String encodedTemporaryPassword = passwordEncoder.encode(TEMPORARY_PASSWORD);

        when(temporaryPasswordRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(encodedTemporaryPassword), Optional.empty());

        when(temporaryPasswordRepository.consumeIfMatches(EMAIL, encodedTemporaryPassword))
                .thenReturn(true);

        // when
        Authentication result = authenticate(TEMPORARY_PASSWORD);

        // then
        assertThat(result.isAuthenticated()).isTrue();

        assertThatThrownBy(() -> authenticate(TEMPORARY_PASSWORD))
                .isInstanceOf(BadCredentialsException.class);

        verify(temporaryPasswordRepository).consumeIfMatches(EMAIL, encodedTemporaryPassword);
    }

    @Test
    @DisplayName("실패 - 비밀번호가 null이면 인증에 실패한다")
    void authenticate_nullPassword_throwsBadCredentialsException() {
        // given
        givenUser();

        // when & then
        assertThatThrownBy(() -> authenticate(null))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(temporaryPasswordRepository);
    }

    @Nested
    @DisplayName("임시 비밀번호 인증 중 Redis 장애")
    class RedisFailureTest {

        @Test
        @DisplayName("Redis 조회 실패 시 인증 서비스 예외가 발생한다")
        void authenticate_redisLookupFailure_throwsAuthenticationServiceException() {
            // given
            givenUser();

            when(temporaryPasswordRepository.findByEmail(EMAIL))
                    .thenThrow(new RedisConnectionFailureException("Redis unavailable"));

            // when & then
            assertThatThrownBy(() -> authenticate(TEMPORARY_PASSWORD))
                    .isInstanceOf(AuthenticationServiceException.class);

            verify(temporaryPasswordRepository, never()).consumeIfMatches(anyString(), anyString());
        }

        @Test
        @DisplayName("Redis 소모 실패 시 인증 서비스 예외가 발생한다")
        void authenticate_redisConsumeFailure_throwsAuthenticationServiceException() {
            // given
            givenUser();

            String encodedTemporaryPassword = passwordEncoder.encode(TEMPORARY_PASSWORD);

            when(temporaryPasswordRepository.findByEmail(EMAIL))
                    .thenReturn(Optional.of(encodedTemporaryPassword));

            when(temporaryPasswordRepository.consumeIfMatches(EMAIL, encodedTemporaryPassword))
                    .thenThrow(new RedisConnectionFailureException("Redis unavailable"));

            // when & then
            assertThatThrownBy(() -> authenticate(TEMPORARY_PASSWORD))
                    .isInstanceOf(AuthenticationServiceException.class);
        }
    }

    @Nested
    @DisplayName("계정 잠금 및 잘못된 임시 비밀번호")
    class AuthenticationFailureTest {

        @Test
        @DisplayName("잠긴 계정은 기존 비밀번호로 로그인할 수 없다")
        void authenticate_lockedAccount_rejectsOriginalPassword() {
            // given
            User user = new User(EMAIL, passwordEncoder.encode(ORIGINAL_PASSWORD), "테스트 사용자");
            user.changeLocked(true);

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

            // when & then
            assertThatThrownBy(() -> authenticate(ORIGINAL_PASSWORD))
                    .isInstanceOf(LockedException.class);

            verifyNoInteractions(temporaryPasswordRepository);
        }

        @Test
        @DisplayName("잠긴 계정은 임시 비밀번호로 로그인할 수 없고 임시 비밀번호를 소모하지 않는다")
        void authenticate_lockedAccount_doesNotConsumeTemporaryPassword() {
            // given
            User user = new User(EMAIL, passwordEncoder.encode(ORIGINAL_PASSWORD), "테스트 사용자");
            user.changeLocked(true);

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

            // when & then
            assertThatThrownBy(() -> authenticate(TEMPORARY_PASSWORD))
                    .isInstanceOf(LockedException.class);

            verifyNoInteractions(temporaryPasswordRepository);
        }

        @Test
        @DisplayName("틀린 임시 비밀번호를 입력하면 인증에 실패하고 임시 비밀번호를 소모하지 않는다")
        void authenticate_wrongTemporaryPassword_doesNotConsumeTemporaryPassword() {
            // given
            givenUser();

            String encodedTemporaryPassword = passwordEncoder.encode(TEMPORARY_PASSWORD);

            when(temporaryPasswordRepository.findByEmail(EMAIL))
                    .thenReturn(Optional.of(encodedTemporaryPassword));

            // when & then
            assertThatThrownBy(() -> authenticate("wrongPassword123!"))
                    .isInstanceOf(BadCredentialsException.class);

            verify(temporaryPasswordRepository).findByEmail(EMAIL);
            verify(temporaryPasswordRepository, never()).consumeIfMatches(anyString(), anyString());
        }
    }

    private void givenUser() {
        User user = new User(
                EMAIL,
                passwordEncoder.encode(ORIGINAL_PASSWORD),
                "테스트 사용자"
        );

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    private Authentication authenticate(String password) {
        return authenticationProvider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(EMAIL, password)
        );
    }
}
