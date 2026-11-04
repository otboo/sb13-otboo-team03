
package com.otboo.team03.global.security;

import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.RedisTemporaryPasswordRepository;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtbooUserDetailsServiceTemporaryPasswordTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisTemporaryPasswordRepository temporaryPasswordRepository;

    @InjectMocks
    private OtbooUserDetailsService userDetailsService;

    @Nested
    @DisplayName("로그인 비밀번호 선택")
    class LoadUserByUsernameTest {

        @Test
        @DisplayName("임시 비밀번호가 없으면 기존 비밀번호 해시를 반환한다")
        void loadUserByUsername_withoutTemporaryPassword_returnsOriginalPassword() {
            // given
            String email = "user@example.com";
            User user = new User(email, "encodedOriginalPassword", "일반 사용자");

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(temporaryPasswordRepository.findByEmail(email)).thenReturn(Optional.empty());

            // when
            UserDetails result = userDetailsService.loadUserByUsername(email);

            // then
            assertThat(result.getPassword()).isEqualTo("encodedOriginalPassword");
        }

        @Test
        @DisplayName("임시 비밀번호가 유효하면 임시 비밀번호 해시를 반환한다")
        void loadUserByUsername_withTemporaryPassword_returnsTemporaryPassword() {
            // given
            String email = "user@example.com";
            User user = new User(email, "encodedOriginalPassword", "일반 사용자");

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(temporaryPasswordRepository.findByEmail(email))
                    .thenReturn(Optional.of("encodedTemporaryPassword"));

            // when
            UserDetails result = userDetailsService.loadUserByUsername(email);

            // then
            assertThat(result.getPassword()).isEqualTo("encodedTemporaryPassword");
            assertThat(result.getPassword()).isNotEqualTo(user.getPassword());
        }

        @Test
        @DisplayName("임시 비밀번호 만료 후 기존 비밀번호 해시를 반환한다")
        void loadUserByUsername_expiredTemporaryPassword_returnsOriginalPassword() {
            // given
            String email = "user@example.com";
            User user = new User(email, "encodedOriginalPassword", "일반 사용자");

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(temporaryPasswordRepository.findByEmail(email))
                    .thenReturn(Optional.of("encodedTemporaryPassword"), Optional.empty());

            // when
            UserDetails beforeExpiration = userDetailsService.loadUserByUsername(email);
            UserDetails afterExpiration = userDetailsService.loadUserByUsername(email);

            // then
            assertThat(beforeExpiration.getPassword()).isEqualTo("encodedTemporaryPassword");
            assertThat(afterExpiration.getPassword()).isEqualTo("encodedOriginalPassword");
        }
    }
}
