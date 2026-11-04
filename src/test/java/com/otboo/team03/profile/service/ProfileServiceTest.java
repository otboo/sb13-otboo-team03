package com.otboo.team03.profile.service;

import com.otboo.team03.profile.dto.ProfileDto;
import com.otboo.team03.profile.dto.Gender;
import com.otboo.team03.profile.entity.Profile;
import com.otboo.team03.profile.exception.ProfileNotFoundException;
import com.otboo.team03.profile.mapper.ProfileDtoAssembler;
import com.otboo.team03.profile.repository.ProfileRepository;
import com.otboo.team03.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

  @Mock
  private ProfileRepository profileRepository;

  @Mock
  private ProfileDtoAssembler profileDtoAssembler;

  @InjectMocks
  private ProfileServiceImpl profileService;

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
}
