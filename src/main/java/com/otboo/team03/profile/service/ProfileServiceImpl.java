package com.otboo.team03.profile.service;

import com.otboo.team03.profile.dto.ProfileDto;
import com.otboo.team03.profile.entity.Profile;
import com.otboo.team03.profile.exception.ProfileNotFoundException;
import com.otboo.team03.profile.mapper.ProfileDtoAssembler;
import com.otboo.team03.profile.repository.ProfileRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileServiceImpl implements ProfileService {
  private final ProfileRepository profileRepository;
  private final ProfileDtoAssembler profileDtoAssembler;

  @Override
  public ProfileDto getProfile(UUID userId) {

    // DB에서 프로필을 찾고, 없으면 우리가 만든 예외(404)를 던집니다!
    Profile profile = profileRepository.findById(userId)
        .orElseThrow(ProfileNotFoundException::new);

    // 찾은 엔티티를 클라이언트 응답용 DTO로 변환하여 반환합니다.
    return profileDtoAssembler.toDto(profile);
  }

}
