
package com.otboo.team03.user.service;

import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.exception.UserNotFoundException;
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

            doThrow(new MailSendException("메일 발송 실패"))
                    .when(mailService).sendTemporaryPassword(eq(email), anyString());

            // when & then
            assertThatThrownBy(() -> passwordResetService.resetPassword(email))
                    .isInstanceOf(MailSendException.class);

            verify(temporaryPasswordRepository).save(email, "encodedTemporaryPassword");
            verify(temporaryPasswordRepository).delete(email);
        }

        @Test
        @DisplayName("성공 - 재발급 시 임시 비밀번호를 새로 저장하고 이메일을 발송한다")
        void resetPassword_reissue_replacesTemporaryPassword() {
            // given
            String email = "user@example.com";
            User user = new User(email, "encodedPassword", "일반 사용자");

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(anyString()))
                    .thenReturn("encodedFirstPassword", "encodedSecondPassword");

            // when
            passwordResetService.resetPassword(email);
            passwordResetService.resetPassword(email);

            // then
            verify(temporaryPasswordRepository).save(email, "encodedFirstPassword");
            verify(temporaryPasswordRepository).save(email, "encodedSecondPassword");
            verify(mailService, times(2)).sendTemporaryPassword(eq(email), anyString());
        }
    }
}
