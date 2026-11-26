
package com.otboo.team03.global.security;

import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtbooUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OtbooUserDetailsService userDetailsService;

    @Nested
    @DisplayName("이메일 기반 사용자 조회")
    class LoadUserByUsernameTest {

        @Test
        @DisplayName("성공 - 사용자 조회 시 기존 비밀번호 해시를 반환한다")
        void loadUserByUsername_returnsOriginalPassword() {
            // given
            String email = "user@example.com";
            User user = new User(email, "encodedOriginalPassword", "일반 사용자");

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

            // when
            UserDetails result = userDetailsService.loadUserByUsername(email);

            // then
            assertThat(result.getUsername()).isEqualTo(email);
            assertThat(result.getPassword()).isEqualTo(user.getPassword());
        }

        @Test
        @DisplayName("실패 - 사용자가 존재하지 않으면 예외가 발생한다")
        void loadUserByUsername_userNotFound_throwsException() {
            // given
            String email = "unknown@example.com";

            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userDetailsService.loadUserByUsername(email))
                    .isInstanceOf(UsernameNotFoundException.class);
        }
    }
}
