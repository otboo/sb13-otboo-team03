package com.otboo.team03.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private MailService mailService;

    @Nested
    @DisplayName("임시 비밀번호 이메일 발송")
    class SendTemporaryPasswordTest {

        @Test
        @DisplayName("성공 - 임시 비밀번호와 유효기간을 이메일로 발송한다")
        void sendTemporaryPassword_success() {
            // given
            String email = "user@example.com";
            String temporaryPassword = "temporary1!!";

            // when
            mailService.sendTemporaryPassword(email, temporaryPassword);

            // then
            var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());

            SimpleMailMessage message = captor.getValue();
            assertThat(message.getTo()).containsExactly(email);
            assertThat(message.getSubject()).contains("임시 비밀번호");
            assertThat(message.getText()).contains(temporaryPassword, "3분");
        }

        @Test
        @DisplayName("실패 - 이메일 발송 오류가 발생하면 예외를 전파한다")
        void sendTemporaryPassword_failure() {
            // given
            String email = "user@example.com";
            String temporaryPassword = "temporary1!!";

            doThrow(new MailSendException("메일 발송 실패"))
                    .when(mailSender).send(any(SimpleMailMessage.class));

            // when & then
            assertThatThrownBy(() -> mailService.sendTemporaryPassword(email, temporaryPassword))
                    .isInstanceOf(MailSendException.class);
        }
    }
}
