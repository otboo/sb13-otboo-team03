package com.otboo.team03.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Nested
    @DisplayName("인증·인가 예외 처리")
    class SecurityExceptionTest {

        @Test
        @DisplayName("접근 권한 부족 시 403 Forbidden 반환")
        void handleAccessDeniedException_returns403() {
            // given
            AccessDeniedException exception = new AccessDeniedException("Access denied");

            // when
            ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccessDeniedException(exception);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().exceptionName()).isEqualTo(ErrorCode.ACCESS_DENIED.name());
        }

        @Test
        @DisplayName("인증 실패 시 401 Unauthorized 반환")
        void handleAuthenticationException_returns401() {
            // given
            AuthenticationException exception = new BadCredentialsException("Authentication required");

            // when
            ResponseEntity<ErrorResponse> response = exceptionHandler.handleAuthenticationException(exception);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().exceptionName()).isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED.name());
        }
    }

}