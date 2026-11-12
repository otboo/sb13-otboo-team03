package com.otboo.team03.global.security;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticatedUserIdExtractorTest {

    @Test
    void 인증된_사용자의_ID를_추출한다() {
        UUID userId = UUID.randomUUID();
        OtbooUserDetails userDetails = new OtbooUserDetails(
                new UserDto(
                        userId,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        "user@otboo.io",
                        "사용자",
                        UserRole.USER,
                        false
                ),
                "encoded-password"
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );

        assertThat(AuthenticatedUserIdExtractor.extract(authentication)).isEqualTo(userId);
    }

    @Test
    void 인증_정보가_없으면_예외가_발생한다() {
        assertAuthenticationRequired(null);
    }

    @Test
    void 인증되지_않은_사용자는_예외가_발생한다() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(false);

        assertAuthenticationRequired(authentication);
    }

    @Test
    void 잘못된_Principal이면_예외가_발생한다() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("wrong-principal");

        assertAuthenticationRequired(authentication);
    }

    private void assertAuthenticationRequired(Authentication authentication) {
        assertThatThrownBy(() -> AuthenticatedUserIdExtractor.extract(authentication))
                .isInstanceOfSatisfying(OtbooException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED)
                );
    }
}
