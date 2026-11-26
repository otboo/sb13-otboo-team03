package com.otboo.team03.profile.mapper;

import com.otboo.team03.global.storage.ImageStorage;
import com.otboo.team03.profile.dto.Gender;
import com.otboo.team03.profile.dto.ProfileDto;
import com.otboo.team03.profile.entity.Profile;
import com.otboo.team03.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProfileDtoAssemblerTest {

  @Mock
  private ImageStorage imageStorage;

  // 테스트용 Profile 엔티티를 편하게 만드는 도우미 메서드
  private Profile createProfile(String imageUrl) {
    User user = new User("user@otboo.io", "pass1234!", "우디");
    return new Profile(
        user,
        imageUrl,
        Gender.MALE,
        LocalDate.of(1995, 5, 20),
        37.5665,
        126.9780,
        3,
        List.of("서울특별시", "중구")
    );
  }

  @Test
  @DisplayName("1. S3 Key 형태의 이미지인 경우 서명 URL로 변환하여 반환한다")
  void toDto_withS3Key_generatesPresignedUrl() {

    // given
    ProfileDtoAssembler assembler = new ProfileDtoAssembler(Optional.of(imageStorage));
    Profile profile = createProfile("profiles/avatar_123.jpg");
    given(imageStorage.getAccessUrl("profiles/avatar_123.jpg"))
        .willReturn("https://s3.amazonaws.com/presigned-url-123");

    // when
    ProfileDto result = assembler.toDto(profile);

    // then
    assertThat(result.profileImageUrl()).isEqualTo("https://s3.amazonaws.com/presigned-url-123");
  }

  @Test
  @DisplayName("2. 기존 https 형태의 URL인 경우 변환 없이 그대로 반환한다")
  void toDto_withHttpsUrl_keepsOriginalUrl() {

    // given
    ProfileDtoAssembler assembler = new ProfileDtoAssembler(Optional.of(imageStorage));
    Profile profile = createProfile("https://cdn.otboo.io/legacy-image.png");

    // when
    ProfileDto result = assembler.toDto(profile);

    // then
    assertThat(result.profileImageUrl()).isEqualTo("https://cdn.otboo.io/legacy-image.png");
  }

  @Test
  @DisplayName("3. 이미지가 null인 경우 null을 반환한다")
  void toDto_withNullImage_returnsNull() {

    // given: 이미지를 null로 넣어 프로필 생성
    ProfileDtoAssembler assembler = new ProfileDtoAssembler(Optional.of(imageStorage));
    Profile profile = createProfile(null);

    // when
    ProfileDto result = assembler.toDto(profile);

    // then: 반환된 DTO의 이미지 URL도 null이어야 함!
    assertThat(result.profileImageUrl()).isNull();
  }

  @Test
  @DisplayName("4. ImageStorage 빈이 없는 환경(Optional.empty())에서도 에러 없이 null을 반환한다")
  void toDto_withoutImageStorage_returnsNullGracefully() {

    // given: 빈이 없는 상황을 테스트하기 위해 Optional.empty()를 주입!
    ProfileDtoAssembler assembler = new ProfileDtoAssembler(Optional.empty());
    Profile profile = createProfile("profiles/avatar_123.jpg");

    // when
    ProfileDto result = assembler.toDto(profile);

    // then: NPE 에러 없이 안전하게 null이어야 함!
    assertThat(result.profileImageUrl()).isNull();
  }

  @Test
  @DisplayName("5. 기존 http (비보안) URL인 경우에도 변환 없이 그대로 반환한다")
  void toDto_withInsecureHttpUrl_keepsOriginalUrl() { // ← 메서드명을 InsecureHttp 로 변경!

    // given
    ProfileDtoAssembler assembler = new ProfileDtoAssembler(Optional.of(imageStorage));
    Profile profile = createProfile("http://cdn.otboo.io/legacy-image.png");

    // when
    ProfileDto result = assembler.toDto(profile);

    // then
    assertThat(result.profileImageUrl()).isEqualTo("http://cdn.otboo.io/legacy-image.png");
  }

  @Test
  @DisplayName("6. User가 null이거나 기타 모든 필드(좌표, 이름 등)가 ProfileDto로 완벽히 조립되는지 검증")
  void toDto_fullFieldsAndNullUser_coveredSuccessfully() {

    // given: User가 null인 프로필 생성 (ProfileDto 삼항연산자 분기 커버!)
    Profile profileWithoutUser = new Profile(
        null,
        null,
        Gender.FEMALE,
        LocalDate.of(2000, 1, 1),
        35.1796,
        129.0756,
        1,
        List.of("부산광역시")
    );
    ProfileDtoAssembler assembler = new ProfileDtoAssembler(Optional.empty());

    // when
    ProfileDto result = assembler.toDto(profileWithoutUser);

    // then: ProfileDto의 모든 Record 게터 메서드 실행 (커버리지 100% 달성)
    assertThat(result.userId()).isNull();
    assertThat(result.name()).isNull();
    assertThat(result.gender()).isEqualTo(Gender.FEMALE);
    assertThat(result.birthDate()).isEqualTo(LocalDate.of(2000, 1, 1));
    assertThat(result.temperatureSensitivity()).isEqualTo(1);
    assertThat(result.location().latitude()).isEqualTo(35.1796);
    assertThat(result.location().longitude()).isEqualTo(129.0756);
    assertThat(result.location().locationNames()).containsExactly("부산광역시");
  }

  @Test
  @DisplayName("7. 지역명(locationNames)이 빈 배열인 경우 location은 null을 반환한다")
  void toDto_withEmptyLocationNames_returnsNullLocation() {
    // given: 좌표는 있지만 지역명이 빈 리스트([])인 프로필 생성
    User user = new User("user@otboo.io", "pass1234!", "우디");
    Profile profile = new Profile(
        user,
        null,
        Gender.MALE,
        LocalDate.of(1995, 5, 20),
        37.5665,
        126.9780,
        3,
        List.of() // 빈 리스트
    );
    ProfileDtoAssembler assembler = new ProfileDtoAssembler(Optional.empty());
    // when
    ProfileDto result = assembler.toDto(profile);
    // then: location 객체가 생성되지 않고 null이어야 함!
    assertThat(result.location()).isNull();
  }
  @Test
  @DisplayName("8. 지역명(locationNames)이 null인 경우 location은 null을 반환한다")
  void toDto_withNullLocationNames_returnsNullLocation() {
    // given: 지역명이 null인 프로필 생성
    User user = new User("user@otboo.io", "pass1234!", "우디");
    Profile profile = new Profile(
        user,
        null,
        Gender.MALE,
        LocalDate.of(1995, 5, 20),
        37.5665,
        126.9780,
        3,
        null // null
    );
    ProfileDtoAssembler assembler = new ProfileDtoAssembler(Optional.empty());
    // when
    ProfileDto result = assembler.toDto(profile);
    // then: location이 null이어야 함!
    assertThat(result.location()).isNull();
  }
  @Test
  @DisplayName("9. 위도와 경도 좌표가 모두 null인 경우 location은 null을 반환한다")
  void toDto_withNullCoordinates_returnsNullLocation() {
    // given: 지역명은 있어도 좌표가 둘 다 null인 비정상 데이터 방어
    User user = new User("user@otboo.io", "pass1234!", "우디");
    Profile profile = new Profile(
        user,
        null,
        Gender.MALE,
        LocalDate.of(1995, 5, 20),
        null, // latitude null
        null, // longitude null
        3,
        List.of("서울특별시")
    );
    ProfileDtoAssembler assembler = new ProfileDtoAssembler(Optional.empty());
    // when
    ProfileDto result = assembler.toDto(profile);
    // then: location이 null이어야 함!
    assertThat(result.location()).isNull();
  }


}