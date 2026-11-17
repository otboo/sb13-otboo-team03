
package com.otboo.team03.user.service;

import com.otboo.team03.auth.repository.SocialAccountRepository;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.exception.UserNotFoundException;
import com.otboo.team03.user.repository.RedisPasswordResetRateLimitRepository;
import com.otboo.team03.user.repository.RedisTemporaryPasswordRepository;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisTemporaryPasswordRepository temporaryPasswordRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private MailService mailService;

    @Mock
    private RedisPasswordResetRateLimitRepository rateLimitRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @InjectMocks
    private PasswordResetService passwordResetService;

    @Nested
    @DisplayName("비밀번호 초기화")
    class ResetPasswordTest {

        @Test
        @DisplayName("성공 - 임시 비밀번호를 암호화하여 저장하고 이메일로 발송한다")
        void resetPassword_success() {
            // given
            String email = "user@example.com";
            User user = new User(email, "encodedPassword", "일반 사용자");

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(anyString())).thenReturn("encodedTemporaryPassword");
            when(rateLimitRepository.tryAcquire(email)).thenReturn(Optional.of("token"));

            // when
            passwordResetService.resetPassword(email);

            // then
            ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);

            verify(passwordEncoder).encode(passwordCaptor.capture());
            String temporaryPassword = passwordCaptor.getValue();

            assertThat(temporaryPassword).isNotBlank();
            verify(temporaryPasswordRepository).save(email, "encodedTemporaryPassword");
            verify(mailService).sendTemporaryPassword(email, temporaryPassword);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 이메일이면 예외가 발생한다")
        void resetPassword_userNotFound_throwsException() {
            // given
            String email = "unknown@example.com";

            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> passwordResetService.resetPassword(email))
                    .isInstanceOf(UserNotFoundException.class);

            verifyNoInteractions(temporaryPasswordRepository, passwordEncoder, mailService);
        }

        @Test
        @DisplayName("실패 - 이메일 발송 실패 시 임시 비밀번호를 삭제한다")
        void resetPassword_mailFailure_deletesTemporaryPassword() {
            // given
            String email = "user@example.com";
            User user = new User(email, "encodedPassword", "일반 사용자");

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(anyString())).thenReturn("encodedTemporaryPassword");
            when(rateLimitRepository.tryAcquire(email)).thenReturn(Optional.of("token"));

            doThrow(new MailSendException("메일 발송 실패"))
                    .when(mailService).sendTemporaryPassword(eq(email), anyString());

            // when & then
            assertThatThrownBy(() -> passwordResetService.resetPassword(email))
                    .isInstanceOf(MailSendException.class);

            verify(temporaryPasswordRepository).save(email, "encodedTemporaryPassword");
            verify(temporaryPasswordRepository).delete(email);
            verify(rateLimitRepository).release(email, "token");
        }

        @Test
        @DisplayName("실패 - 동일 이메일로 연속 요청하면 재발급을 차단하고 이메일을 중복 발송하지 않는다")
        void resetPassword_repeatedRequest_isRateLimited() {
            // given
            String email = "user@example.com";
            User user = new User(email, "encodedPassword", "일반 사용자");

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(anyString())).thenReturn("encodedTemporaryPassword");
            when(rateLimitRepository.tryAcquire(email))
                    .thenReturn(Optional.of("token"), Optional.empty());

            // when
            passwordResetService.resetPassword(email);

            // then
            assertThatThrownBy(() -> passwordResetService.resetPassword(email))
                    .isInstanceOf(OtbooException.class);

            verify(temporaryPasswordRepository).save(email, "encodedTemporaryPassword");
            verify(mailService).sendTemporaryPassword(eq(email), anyString());
        }

        @Test
        @DisplayName("실패 - 메일 발송과 정리 작업이 모두 실패해도 최초 예외를 보존한다")
        void resetPassword_mailAndCleanupFailures_preservesOriginalException() {
            // given
            String email = "user@example.com";
            User user = new User(email, "encodedPassword", "일반 사용자");

            MailSendException mailException = new MailSendException("메일 발송 실패");
            RuntimeException deleteException = new IllegalStateException("임시 비밀번호 삭제 실패");
            RuntimeException releaseException = new IllegalStateException("요청 제한 해제 실패");

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(rateLimitRepository.tryAcquire(email)).thenReturn(Optional.of("token"));
            when(passwordEncoder.encode(anyString())).thenReturn("encodedTemporaryPassword");

            doThrow(mailException).when(mailService).sendTemporaryPassword(eq(email), anyString());
            doThrow(deleteException).when(temporaryPasswordRepository).delete(email);
            doThrow(releaseException).when(rateLimitRepository).release(email, "token");

            // when & then
            assertThatThrownBy(() -> passwordResetService.resetPassword(email))
                    .isSameAs(mailException)
                    .satisfies(exception -> assertThat(exception.getSuppressed())
                            .containsExactly(deleteException, releaseException));

            verify(temporaryPasswordRepository).delete(email);
            verify(rateLimitRepository).release(email, "token");
        }
    }
}
