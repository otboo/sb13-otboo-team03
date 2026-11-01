package com.otboo.team03.profile.entity;
import com.otboo.team03.profile.dto.Gender;
import com.otboo.team03.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import com.otboo.team03.profile.entity.Profile;
import com.otboo.team03.profile.dto.Gender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileTest {
  @Test
  @DisplayName("정상적인 정보로 프로필을 생성할 수 있다")
  void createProfile_success() {
    // given
    User user = new User("user@otboo.io", "password123!", "우디");
    Gender gender = Gender.MALE;
    LocalDate birthDate = LocalDate.of(1995, 5, 20);
    Double latitude = 37.5665;
    Double longitude = 126.9780;
    Integer temperatureSensitivity = 3;
    List<String> locationNames = List.of("서울특별시", "중구", "명동");
    // when
    Profile profile = new Profile(
        user,
        "https://cdn.otboo.io/profiles/sample.png",
        gender,
        birthDate,
        latitude,
        longitude,
        temperatureSensitivity,
        locationNames
    );
    // then
    assertThat(profile.getUser()).isEqualTo(user);
    assertThat(profile.getGender()).isEqualTo(Gender.MALE);
    assertThat(profile.getTemperatureSensitivity()).isEqualTo(3);
    assertThat(profile.getLocationNames()).containsExactly("서울특별시", "중구", "명동");
  }
  @Test
  @DisplayName("온도 민감도가 1~5 범위를 벗어나면 예외가 발생한다")
  void createProfile_invalidTemperatureSensitivity() {
    // given
    User user = new User("user@otboo.io", "password123!", "우디");
    // when & then: 1 미만(0)일 때 예외 발생
    assertThatThrownBy(() -> new Profile(user, null, Gender.MALE, null, null, null, 0, null))
        .isInstanceOf(IllegalArgumentException.class);
    // when & then: 5 초과(6)일 때 예외 발생
    assertThatThrownBy(() -> new Profile(user, null, Gender.MALE, null, null, null, 6, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}