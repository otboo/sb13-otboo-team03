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
        User user = new User(email, encodedPassword, name);

        // then
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getName()).isEqualTo(name);
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.isLocked()).isFalse();
    }

    @Test
    @DisplayName("사용자 이름을 변경할 수 있다")
    void updateName_success() {
        // given
        User user = new User("test@example.com", "encodedPassword", "기존 이름");

        // when
        user.updateName("변경된 이름");

        // then
        assertThat(user.getName()).isEqualTo("변경된 이름");
    }
}