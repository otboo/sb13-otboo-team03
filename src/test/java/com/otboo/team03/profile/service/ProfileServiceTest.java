package com.otboo.team03.profile.service;

import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.global.storage.ImageStorage;
import com.otboo.team03.profile.dto.ProfileDto;
import com.otboo.team03.profile.dto.Gender;
import com.otboo.team03.profile.dto.ProfileUpdateRequest;
import com.otboo.team03.profile.dto.WeatherAPILocation;
import com.otboo.team03.profile.entity.Profile;
import com.otboo.team03.profile.exception.ProfileNotFoundException;
import com.otboo.team03.profile.mapper.ProfileDtoAssembler;
import com.otboo.team03.profile.repository.ProfileRepository;
import com.otboo.team03.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

  @Mock
  private ProfileRepository profileRepository;

  @Mock
  private ProfileDtoAssembler profileDtoAssembler;

  @Mock
  private ImageStorage imageStorage;

  private ProfileServiceImpl profileService;
  @BeforeEach
  void setUp() {
    profileService = new ProfileServiceImpl(
        profileRepository,
        profileDtoAssembler,
        Optional.of(imageStorage)
    );
  }

  @Test
  @DisplayName("존재하는 사용자의 ID로 조회하면 정상적으로 ProfileDto를 반환한다")
  void getProfile_success() {
    // given
    UUID userId = UUID.randomUUID();
    User user = new User("woody@otboo.io", "pass1234!", "우디");
    Profile profile = new Profile(
        user,
        "https://cdn.otboo.io/profile.png",
        Gender.MALE,
        LocalDate.of(1995, 5, 20),
        37.5665,
        126.9780,
        3,
        List.of("서울특별시", "중구")
    );

    ProfileDto expectedDto = ProfileDto.from(profile,profile.getProfileImageUrl());
    given(profileRepository.findById(userId)).willReturn(Optional.of(profile));
    given(profileDtoAssembler.toDto(profile)).willReturn(expectedDto);
    // when
    ProfileDto result = profileService.getProfile(userId);

    // then
    assertThat(result.name()).isEqualTo("우디");
    assertThat(result.gender()).isEqualTo(Gender.MALE);
    assertThat(result.temperatureSensitivity()).isEqualTo(3);
  }

  @Test
  @DisplayName("존재하지 않는 사용자의 ID로 조회하면 ProfileNotFoundException이 발생한다")
  void getProfile_notFound() {
    // given
    UUID nonExistentUserId = UUID.randomUUID();

    given(profileRepository.findById(nonExistentUserId)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> profileService.getProfile(nonExistentUserId))
        .isInstanceOf(ProfileNotFoundException.class);
  }

  @Test
  @DisplayName("이미지와 수정 요청이 주어지면 S3에 업로드하고 프로필을 정상 수정한다")
  void updateProfile_withImage_success() {
    // given
    UUID userId = UUID.randomUUID();
    User user = new User("woody@otboo.io", "pass1234!", "우디");
    Profile profile = new Profile(user);
    // 수정 요청 DTO 준비
    ProfileUpdateRequest request = new ProfileUpdateRequest(
        "우디새이름", Gender.FEMALE, LocalDate.of(1995, 5, 5), null, 4
    );
    // 가짜 이미지 파일 준비
    MockMultipartFile image = new MockMultipartFile(
        "image", "test.jpg", "image/jpeg", "image-content".getBytes()
    );
    // 반환될 기대 DTO
    ProfileDto expectedDto = ProfileDto.from(profile, "profile/new-key.jpg");
    // 가짜 객체들이 정상 동작하도록 길을 열어주는 설정
    given(profileRepository.findById(userId)).willReturn(Optional.of(profile));
    given(imageStorage.upload(image)).willReturn("profile/new-key.jpg");
    given(profileDtoAssembler.toDto(profile)).willReturn(expectedDto);

    //when
    ProfileDto result = profileService.updateProfile(userId, request, image);

    //then
    assertThat(result).isNotNull();
    assertThat(profile.getProfileImageUrl()).isEqualTo("profile/new-key.jpg");
    assertThat(profile.getGender()).isEqualTo(Gender.FEMALE);
    // S3 업로드가 실제로 실행되었는지 확인하는 핵심 검증
    verify(imageStorage).upload(image);
  }

  @Test
  @DisplayName("이미지 없이 텍스트 정보만 주어지면 S3 업로드 없이 프로필 정보를 정상 수정한다")
  void updateProfile_withoutImage_success() {
    // given
    UUID userId = UUID.randomUUID();
    User user = new User("woody@otboo.io", "pass1234!", "우디");
    // 기존에 이미지가 있던 프로필 준비
    Profile profile = new Profile(
        user, "https://cdn.otboo.io/profiles/old.png", Gender.MALE,
        LocalDate.of(1995, 5, 20), 37.5, 126.9, 3, List.of("서울시")
    );
    // 텍스트 수정 요청 준비
    ProfileUpdateRequest request = new ProfileUpdateRequest(
        "우디", Gender.FEMALE, LocalDate.of(1995, 5, 20), null, 5
    );
    ProfileDto expectedDto = ProfileDto.from(profile, "https://cdn.otboo.io/profiles/old.png");
    given(profileRepository.findById(userId)).willReturn(Optional.of(profile));
    given(profileDtoAssembler.toDto(profile)).willReturn(expectedDto);

    // When (실행 - image 파라미터에 null 전달)
    ProfileDto result = profileService.updateProfile(userId, request, null);
    // Then
    assertThat(result).isNotNull();
    assertThat(profile.getGender()).isEqualTo(Gender.FEMALE); // 성별 변경 확인
    assertThat(profile.getTemperatureSensitivity()).isEqualTo(5); // 민감도 변경 확인
    assertThat(profile.getProfileImageUrl()).isEqualTo("https://cdn.otboo.io/profiles/old.png"); // 기존 이미지 유지 확인
    // imageStorage.upload()가 절대(never) 한 번도 호출되지 않았는지 검증.
    verify(imageStorage, never()).upload(any());
  }

  @Test
  @DisplayName("존재하지 않는 사용자의 ID로 수정을 시도하면 ProfileNotFoundException이 발생한다")
  void updateProfile_notFound() {
    // given
    UUID nonExistentUserId = UUID.randomUUID();
    ProfileUpdateRequest request = new ProfileUpdateRequest(
        "우디", Gender.MALE, LocalDate.of(1995, 5, 20), null, 3
    );

    // 조회 시 빈 Optional 반환 설정
    given(profileRepository.findById(nonExistentUserId)).willReturn(Optional.empty());

    //when & then
    assertThatThrownBy(() -> profileService.updateProfile(nonExistentUserId, request, null))
        .isInstanceOf(ProfileNotFoundException.class);
  }

  @Test
  @DisplayName("스토리지 빈이 없는 환경(Optional.empty())에서 이미지 업로드를 시도하면 INTERNAL_SERVER_ERROR 예외가 발생한다")
  void updateProfile_withoutImageStorage_throwsException() {
    // given
    ProfileServiceImpl noStorageService = new ProfileServiceImpl(
        profileRepository, profileDtoAssembler, Optional.empty()
    );
    UUID userId = UUID.randomUUID();
    User user = new User("woody@otboo.io", "pass1234!", "우디");
    Profile profile = new Profile(user);
    MockMultipartFile image = new MockMultipartFile(
        "image", "test.jpg", "image/jpeg", "image-content".getBytes()
    );
    given(profileRepository.findById(userId)).willReturn(Optional.of(profile));
    // when & then
    assertThatThrownBy(() -> noStorageService.updateProfile(userId, null, image))
        .isInstanceOf(OtbooException.class);
  }

  @Test
  @DisplayName("위치 정보를 포함한 수정 요청이 들어오면 좌표와 지명이 정상 수정된다")
  void updateProfile_withLocation_success() {
    // given
    UUID userId = UUID.randomUUID();
    User user = new User("woody@otboo.io", "pass1234!", "우디");
    Profile profile = new Profile(user);

    // 위치 정보(WeatherAPILocation)가 포함된 요청 준비!
    WeatherAPILocation location = new WeatherAPILocation(37.5665, 126.9780, 60, 127, List.of("서울특별시", "중구"));
    ProfileUpdateRequest request = new ProfileUpdateRequest(
        "우디", Gender.MALE, LocalDate.of(1995, 5, 20), location, 3
    );
    ProfileDto expectedDto = ProfileDto.from(profile, null);
    given(profileRepository.findById(userId)).willReturn(Optional.of(profile));
    given(profileDtoAssembler.toDto(profile)).willReturn(expectedDto);
    // when
    ProfileDto result = profileService.updateProfile(userId, request, null);
    // then: 위치 정보 3가지가 엔티티에 확실히 들어갔는지 검증!
    assertThat(result).isNotNull();
    assertThat(profile.getLatitude()).isEqualTo(37.5665);
    assertThat(profile.getLongitude()).isEqualTo(126.9780);
    assertThat(profile.getLocationNames()).containsExactly("서울특별시", "중구");
  }

  @Test
  @DisplayName("수정 요청에 새 이름이 포함되어 있으면 유저의 이름이 정상 변경된다")
  void updateProfile_withName_changesUserName() {
    // given
    UUID userId = UUID.randomUUID();
    User user = new User("woody@otboo.io", "pass1234!", "원래이름");
    Profile profile = new Profile(user);

    ProfileUpdateRequest request = new ProfileUpdateRequest("우디01", null,  null, null, null);
    ProfileDto expectedDto = ProfileDto.from(profile, null);

    given(profileRepository.findById(userId)).willReturn(Optional.of(profile));
    given(profileDtoAssembler.toDto(profile)).willReturn(expectedDto);

    // when
    ProfileDto result = profileService.updateProfile(userId, request, null);

    // then
    assertThat(result).isNotNull();
    assertThat(user.getName()).isEqualTo(request.name());
  }

  @Test
  @DisplayName("기존 S3 이미지가 있던 프로필에 새 이미지를 업로드하면 기존 S3 이미지가 삭제된다")
  void updateProfile_replacesImage_deletesOldS3Image() {
    // given (기존에 S3 이미지 "profiles/old-key.jpg"를 가지고 있던 프로필 준비)
    UUID userId = UUID.randomUUID();
    User user = new User("woody@otboo.io", "pass1234!", "우디");
    Profile profile = new Profile(
        user, "profiles/old-key.jpg", Gender.MALE, LocalDate.of(1995, 5, 20),
        37.5, 126.9, 3, List.of("서울시")
    );
    // 새 업로드용 가짜 이미지 준비
    MockMultipartFile newImage = new MockMultipartFile(
        "image", "new.jpg", "image/jpeg", "new-content".getBytes()
    );
    ProfileDto expectedDto = ProfileDto.from(profile, "profiles/new-key.jpg");
    given(profileRepository.findById(userId)).willReturn(Optional.of(profile));
    given(imageStorage.upload(newImage)).willReturn("profiles/new-key.jpg");
    given(profileDtoAssembler.toDto(profile)).willReturn(expectedDto);

    // when (새 이미지로 프로필 수정 실행)
    profileService.updateProfile(userId, null, newImage);
    // then
    // 새 이미지가 정상 업로드되었는지 확인
    verify(imageStorage).upload(newImage);
    verify(imageStorage).delete("profiles/old-key.jpg");
  }

  @Test
  @DisplayName("기존 이미지가 외부 URL(https://)인 경우 새 이미지를 올려도 기존 이미지 삭제를 호출하지 않는다")
  void updateProfile_withExternalImageUrl_doesNotDeleteOldImage() {
    // given: 기존 이미지가 외부 URL인 프로필 준비
    UUID userId = UUID.randomUUID();
    User user = new User("woody@otboo.io", "pass1234!", "우디");
    Profile profile = new Profile(
        user, "https://cdn.otboo.io/default-avatar.png", Gender.MALE, LocalDate.of(1995, 5, 20),
        37.5, 126.9, 3, List.of("서울시")
    );
    MockMultipartFile newImage = new MockMultipartFile(
        "image", "new.jpg", "image/jpeg", "new-content".getBytes()
    );
    given(profileRepository.findById(userId)).willReturn(Optional.of(profile));
    given(imageStorage.upload(newImage)).willReturn("profiles/new-key.jpg");
    given(profileDtoAssembler.toDto(profile)).willReturn(ProfileDto.from(profile, "profiles/new-key.jpg"));
    // when
    profileService.updateProfile(userId, null, newImage);
    // then: delete가 절대(never) 호출되지 않았는지 검증!
    verify(imageStorage, never()).delete(any());
  }


}
