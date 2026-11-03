
package com.otboo.team03.auth.service;

import com.otboo.team03.global.dto.JwtRefreshResult;
import com.otboo.team03.global.exception.jwt.InvalidJwtException;
import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.global.security.OtbooUserDetailsService;
import com.otboo.team03.global.security.jwt.JwtInformation;
import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.global.security.jwt.JwtTokenProvider;
import com.otboo.team03.user.dto.response.UserDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtRegistry jwtRegistry;

    @Mock
    private OtbooUserDetailsService userDetailsService;

    @InjectMocks
    private AuthService authService;

    private UUID userId;
    private UserDto userDto;
    private OtbooUserDetails userDetails;

    private static final String REFRESH_TOKEN = "existing-refresh-token";
    private static final String NEW_ACCESS_TOKEN = "new-access-token";
    private static final String NEW_REFRESH_TOKEN = "new-refresh-token";

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        // UserDto의 필드값은 실제 테스트 대상이 아니므로 Mock 사용
        userDto = mock(UserDto.class);

        userDetails = new OtbooUserDetails(userDto, "encoded-password");
    }

    @Nested
    class RefreshSuccessTest {

        @Test
        void refresh_success() {
            // given
            given(jwtRegistry.hasActiveJwtInformationByRefreshToken(REFRESH_TOKEN))
                    .willReturn(true);
            given(jwtTokenProvider.getUserId(REFRESH_TOKEN))
                    .willReturn(userId);
            given(userDetailsService.loadUserById(userId))
                    .willReturn(userDetails);

            given(jwtTokenProvider.createAccessToken(userId))
                    .willReturn(NEW_ACCESS_TOKEN);
            given(jwtTokenProvider.createRefreshToken(userId))
                    .willReturn(NEW_REFRESH_TOKEN);

            // when
            JwtRefreshResult result = authService.refresh(REFRESH_TOKEN);

            // then
            assertThat(result.jwtDto().userDto()).isSameAs(userDto);
            assertThat(result.jwtDto().accessToken())
                    .isEqualTo(NEW_ACCESS_TOKEN);
            assertThat(result.refreshToken())
                    .isEqualTo(NEW_REFRESH_TOKEN);

            then(jwtTokenProvider).should()
                    .validateRefreshToken(REFRESH_TOKEN);

            ArgumentCaptor<JwtInformation> captor =
                    ArgumentCaptor.forClass(JwtInformation.class);

            then(jwtRegistry).should()
                    .rotateJwtInformation(eq(REFRESH_TOKEN), captor.capture());

            JwtInformation registered = captor.getValue();

            assertThat(registered.getUserDto()).isSameAs(userDto);
            assertThat(registered.getAccessToken())
                    .isEqualTo(NEW_ACCESS_TOKEN);
            assertThat(registered.getRefreshToken())
                    .isEqualTo(NEW_REFRESH_TOKEN);
        }

        @Test
        void refresh_rotatesTokensOnce() {
            // given
            given(jwtRegistry.hasActiveJwtInformationByRefreshToken(REFRESH_TOKEN))
                    .willReturn(true);
            given(jwtTokenProvider.getUserId(REFRESH_TOKEN))
                    .willReturn(userId);
            given(userDetailsService.loadUserById(userId))
                    .willReturn(userDetails);
            given(jwtTokenProvider.createAccessToken(userId))
                    .willReturn(NEW_ACCESS_TOKEN);
            given(jwtTokenProvider.createRefreshToken(userId))
                    .willReturn(NEW_REFRESH_TOKEN);

            // when
            authService.refresh(REFRESH_TOKEN);

            // then
            then(jwtRegistry).should(times(1))
                    .rotateJwtInformation(
                            eq(REFRESH_TOKEN),
                            any(JwtInformation.class)
                    );
        }
    }

    @Nested
    class RefreshFailureTest {

        @Test
        void refresh_invalidToken_throwsException() {
            // given
            willThrow(new InvalidJwtException())
                    .given(jwtTokenProvider)
                    .validateRefreshToken(REFRESH_TOKEN);

            // when & then
            assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                    .isInstanceOf(InvalidJwtException.class);

            then(jwtRegistry).shouldHaveNoInteractions();
            then(userDetailsService).shouldHaveNoInteractions();
        }

        @Test
        void refresh_inactiveToken_throwsException() {
            // given
            given(jwtRegistry.hasActiveJwtInformationByRefreshToken(REFRESH_TOKEN))
                    .willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                    .isInstanceOf(InvalidJwtException.class);

            then(userDetailsService).shouldHaveNoInteractions();
            then(jwtTokenProvider).should(never())
                    .createAccessToken(any(UUID.class));
        }

        @Test
        void refresh_lockedUser_throwsException() {
            // given
            given(jwtRegistry.hasActiveJwtInformationByRefreshToken(REFRESH_TOKEN))
                    .willReturn(true);
            given(jwtTokenProvider.getUserId(REFRESH_TOKEN))
                    .willReturn(userId);
            given(userDetailsService.loadUserById(userId))
                    .willReturn(userDetails);
            given(userDto.locked()).willReturn(true);

            // when & then
            assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                    .isInstanceOf(InvalidJwtException.class);

            then(jwtRegistry).should(never())
                    .rotateJwtInformation(anyString(), any());
            then(jwtTokenProvider).should(never())
                    .createAccessToken(any(UUID.class));
        }

        @Test
        void refresh_userNotFound_throwsException() {
            // given
            given(jwtRegistry.hasActiveJwtInformationByRefreshToken(REFRESH_TOKEN))
                    .willReturn(true);
            given(jwtTokenProvider.getUserId(REFRESH_TOKEN))
                    .willReturn(userId);
            given(userDetailsService.loadUserById(userId))
                    .willThrow(new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

            // when & then
            assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                    .isInstanceOf(InvalidJwtException.class);

            then(jwtRegistry).should(never())
                    .rotateJwtInformation(anyString(), any());
        }

        @Test
        void refresh_rotationFailure_throwsException() {
            // given
            given(jwtRegistry.hasActiveJwtInformationByRefreshToken(REFRESH_TOKEN))
                    .willReturn(true);
            given(jwtTokenProvider.getUserId(REFRESH_TOKEN))
                    .willReturn(userId);
            given(userDetailsService.loadUserById(userId))
                    .willReturn(userDetails);

            given(jwtTokenProvider.createAccessToken(userId))
                    .willReturn(NEW_ACCESS_TOKEN);
            given(jwtTokenProvider.createRefreshToken(userId))
                    .willReturn(NEW_REFRESH_TOKEN);

            willThrow(new InvalidJwtException())
                    .given(jwtRegistry)
                    .rotateJwtInformation(
                            eq(REFRESH_TOKEN),
                            any(JwtInformation.class)
                    );

            // when & then
            assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                    .isInstanceOf(InvalidJwtException.class);
        }
    }
}
