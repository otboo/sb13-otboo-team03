
package com.otboo.team03.auth.controller;

import com.otboo.team03.auth.service.AuthService;
import com.otboo.team03.global.config.JwtProperties;
import com.otboo.team03.global.security.jwt.RefreshTokenCookieProvider;
import com.otboo.team03.user.dto.request.ResetPasswordRequest;
import com.otboo.team03.user.exception.UserNotFoundException;
import com.otboo.team03.user.service.PasswordResetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private RefreshTokenCookieProvider refreshTokenCookieProvider;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private PasswordResetService passwordResetService;

    @InjectMocks
    private AuthController authController;

    @Nested
    @DisplayName("비밀번호 초기화")
    class ResetPasswordTest {

        @Test
        @DisplayName("성공 - 이메일로 비밀번호 초기화 요청 시 204 반환")
        void resetPassword_success_returns204() {
            // given
            String email = "user@example.com";
            ResetPasswordRequest request = new ResetPasswordRequest(email);

            // when
            ResponseEntity<Void> response = authController.resetPassword(request);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(response.getBody()).isNull();
            verify(passwordResetService).resetPassword(email);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 이메일이면 예외 전파")
        void resetPassword_userNotFound_throwsException() {
            // given
            String email = "unknown@example.com";
            ResetPasswordRequest request = new ResetPasswordRequest(email);

            doThrow(new UserNotFoundException())
                    .when(passwordResetService).resetPassword(email);

            // when & then
            assertThatThrownBy(() -> authController.resetPassword(request))
                    .isInstanceOf(UserNotFoundException.class);

            verify(passwordResetService).resetPassword(email);
        }
    }
}
