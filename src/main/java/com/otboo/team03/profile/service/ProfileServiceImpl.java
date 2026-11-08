package com.otboo.team03.profile.service;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.global.storage.ImageStorage;
import com.otboo.team03.profile.dto.ProfileDto;
import com.otboo.team03.profile.dto.ProfileUpdateRequest;
import com.otboo.team03.profile.dto.WeatherAPILocation;
import com.otboo.team03.profile.entity.Profile;
import com.otboo.team03.profile.exception.ProfileNotFoundException;
import com.otboo.team03.profile.mapper.ProfileDtoAssembler;
import com.otboo.team03.profile.repository.ProfileRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileServiceImpl implements ProfileService {
  private final ProfileRepository profileRepository;
  private final ProfileDtoAssembler profileDtoAssembler;
  private final Optional<ImageStorage> imageStorage;

  @Override
  public ProfileDto getProfile(UUID userId) {

    // DB에서 프로필을 찾고, 없으면 우리가 만든 예외(404)를 던집니다!
    Profile profile = profileRepository.findById(userId)
        .orElseThrow(ProfileNotFoundException::new);

    // 찾은 엔티티를 클라이언트 응답용 DTO로 변환하여 반환합니다.
    return profileDtoAssembler.toDto(profile);
  }

  @Override
  @Transactional
  public ProfileDto updateProfile(UUID userId, ProfileUpdateRequest request, MultipartFile image) {
    // 1. DB에서 기존 프로필 조회 (없으면 404 예외)
    Profile profile = profileRepository.findById(userId)
        .orElseThrow(ProfileNotFoundException::new);

    //  2. 새 이미지 파일이 들어온 경우에만 S3 업로드 후 엔티티 반영
    if (image != null && !image.isEmpty()) {
      ImageStorage storage = imageStorage.orElseThrow(() ->
          new OtbooException(ErrorCode.INTERNAL_SERVER_ERROR));
      String imageKey = storage.upload(image);
      profile.updateProfileImageUrl(imageKey);
    }

    //  3. 위치 정보 꺼내기 (request가 null이어도 안전하게 방어)
    WeatherAPILocation loc = (request != null) ? request.location() : null;
    Double latitude = (loc != null) ? loc.latitude() : null;
    Double longitude = (loc != null) ? loc.longitude() : null;
    List<String> locationNames = (loc != null) ? loc.locationNames() : null;

    // 4. 엔티티 부분 수정 호출 (request가 null이 아닐 때만 값 전달)
    if (request != null) {
      profile.update(request.gender(), request.birthDate(), latitude, longitude,
          request.temperatureSensitivity(), locationNames);
    }

      // 5. 수정된 프로필을 DTO로 조립하여 반환
      return profileDtoAssembler.toDto(profile);
  }

}