
package com.otboo.team03.global.security.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class JwtLoginFailureHandlerTest {

    private final JwtLoginFailureHandler handler = new JwtLoginFailureHandler(new ObjectMapper());

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 401을 반환한다")
    void onAuthenticationFailure_badCredentials_returns401() throws Exception {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.onAuthenticationFailure(
                new MockHttpServletRequest(),
                response,
                new BadCredentialsException("Bad credentials")
        );

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTHENTICATION_FAILED");
    }

    @Test
    @DisplayName("인증 서비스 장애가 발생하면 503을 반환하고 내부 오류를 노출하지 않는다")
    void onAuthenticationFailure_serviceUnavailable_returns503() throws Exception {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.onAuthenticationFailure(
                new MockHttpServletRequest(),
                response,
                new AuthenticationServiceException("Redis connection failed")
        );

        // then
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("AUTHENTICATION_SERVICE_UNAVAILABLE");
        assertThat(response.getContentAsString()).doesNotContain("Redis connection failed");
    }
}
