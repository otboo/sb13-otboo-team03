package com.otboo.team03.profile.entity;
import com.otboo.team03.profile.dto.Gender;
import com.otboo.team03.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;

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

  @Test
  @DisplayName("Persistable 인터페이스 메서드가 정상 동작한다")
  void persistableMethods() {
    // given
    User user = new User("user@otboo.io", "password123!", "우디");
    Profile profile = new Profile(user, null, Gender.MALE, null, null, null, 3, null);

    // then
    assertThat(profile.getId()).isEqualTo(user.getId());
    assertThat(profile.isNew()).isTrue();

    profile.markNotNew();
    assertThat(profile.isNew()).isTrue(); // createdAt이 아직 null이므로 isNew()는 true
  }

  @Test
  @DisplayName("모든 필드를 새 값으로 수정하면 정상적으로 변경된다.")
  void update_allFields_success() {
    // given
    User user = new User("user@otboo.io", "password123!", "우디");
    Profile profile = new Profile(
        user,
        "https://cdn.otboo.io/profiles/sample.png",
        Gender.MALE,
        LocalDate.of(2000, 1, 1),
        37.5,
        126.9,
        3,
        List.of("서울시")
    );

    // When
    profile.update(
        Gender.FEMALE,                 // 새 성별
        LocalDate.of(1995, 5, 5),      // 새 생년월일
        35.1,                          // 새 위도
        129.0,                         // 새 경도
        4,                             // 새 온도 민감도
        List.of("부산시")               // 새 지명 목록
    );

    // then
    assertThat(profile.getGender()).isEqualTo(Gender.FEMALE);
    assertThat(profile.getBirthDate()).isEqualTo(LocalDate.of(1995, 5, 5));
    assertThat(profile.getLatitude()).isEqualTo(35.1);
    assertThat(profile.getLongitude()).isEqualTo(129.0);
    assertThat(profile.getTemperatureSensitivity()).isEqualTo(4);
    assertThat(profile.getLocationNames()).containsExactly("부산시");
  }

  @Test
  @DisplayName("null로 전달된 필드는 기존 값을 그대로 유지한다 (부분 수정)")
  void update_nullFields_keepExistingValues() {
    // given
    User user = new User("user@otboo.io", "password123!", "우디");
    Profile profile = new Profile(
        user,
        "https://cdn.otboo.io/profiles/sample.png",
        Gender.MALE,
        LocalDate.of(2000, 1, 1),
        37.5,
        126.9,
        3,
        List.of("서울시")
    );

    // when
    profile.update(null, null, null, null, null, null);

    // then
    assertThat(profile.getGender()).isEqualTo(Gender.MALE);
    assertThat(profile.getBirthDate()).isEqualTo(LocalDate.of(2000, 1, 1));
    assertThat(profile.getLatitude()).isEqualTo(37.5);
    assertThat(profile.getLongitude()).isEqualTo(126.9);
    assertThat(profile.getTemperatureSensitivity()).isEqualTo(3);
    assertThat(profile.getLocationNames()).containsExactly("서울시");
  }

  @Test
  @DisplayName("프로필 이미지 URL을 정상적으로 수정할 수 있다")
  void updateProfileImageUrl_success() {
    // given
    User user = new User("user@otboo.io", "password123!", "우디");
    Profile profile = new Profile(
        user,
        "https://cdn.otboo.io/profiles/sample.png",
        Gender.MALE,
        LocalDate.of(2000, 1, 1),
        37.5,
        126.9,
        3,
        List.of("서울시")
    );

    // when
    profile.updateProfileImageUrl("profile/new-image.jpg");

    // then
    assertThat(profile.getProfileImageUrl()).isEqualTo("profile/new-image.jpg");
  }

  @Test
  @DisplayName("User만 전달하여 초기 빈 프로필을 정상적으로 생성할 수 있다 (회원가입 연동)")
  void createProfile_userOnly_success() {
    // given
    User user = new User("user@otboo.io", "password123!", "우디");
    // when (방금 만든 편의 생성자 호출!)
    Profile profile = new Profile(user);
    // then
    assertThat(profile.getUser()).isEqualTo(user);
    assertThat(profile.getGender()).isNull();
    assertThat(profile.getLocationNames()).isEmpty();
  }
  @Test
  @DisplayName("수정 시 온도 민감도가 1~5 범위를 벗어나면 예외가 발생한다")
  void update_invalidTemperatureSensitivity_throwsException() {
    // given
    User user = new User("user@otboo.io", "password123!", "우디");
    Profile profile = new Profile(user);
    // when & then: 0이나 6처럼 잘못된 온도로 수정하려고 하면 예외 발생!
    assertThatThrownBy(() -> profile.update(null, null, null, null, 6, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("온도 민감도는 1에서 5 사이여야 합니다.");
  }

}