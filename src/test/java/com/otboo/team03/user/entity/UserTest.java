package com.otboo.team03.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    @DisplayName("사용자 생성 시 기본 권한은 USER이고 잠금 상태는 false이다")
    void createUser() {
        // given
        String email = "test@example.com";
        String encodedPassword = "encodedPassword";
        String name = "테스트 사용자";

        // when
        User user = User.create(email, encodedPassword, name);

        // then
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getName()).isEqualTo(name);
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.isLocked()).isFalse();
    }
}