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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
      // 1단계: 기존 이미지 key를 변수에 미리 보관해 둡니다.
      String oldImageKey = profile.getProfileImageUrl();
      // 2단계: 새 이미지를 S3에 업로드하고 프로필에 반영합니다.
      String imageKey = storage.upload(image);
      profile.updateProfileImageUrl(imageKey);
      // 3단계: 기존 이미지가 존재하고, 외부 URL(http/https)이 아닌 S3 키 형태일 때만 삭제!
      if (oldImageKey != null
          && !oldImageKey.startsWith("http://")
          && !oldImageKey.startsWith("https://")) {
        // 트랜잭션이 활성화된 상태라면 DB 커밋이 완료된 직후(afterCommit)에 삭제 실행
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
          TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              storage.delete(oldImageKey);
            }
          });
        } else {
          storage.delete(oldImageKey);
        }
      }
    }

    //  3. 위치 정보 꺼내기 (request가 null이어도 안전하게 방어)
    WeatherAPILocation loc = (request != null) ? request.location() : null;
    Double latitude = (loc != null) ? loc.latitude() : null;
    Double longitude = (loc != null) ? loc.longitude() : null;
    List<String> locationNames = (loc != null) ? loc.locationNames() : null;

    // 4. 엔티티 부분 수정 호출 (request가 null이 아닐 때만 값 전달)
    if (request != null) {
      if (request.name() != null && profile.getUser() != null) {
        profile.getUser().changeName(request.name());
      }

      profile.update(request.gender(), request.birthDate(), latitude, longitude,
          request.temperatureSensitivity(), locationNames);
    }

      // 5. 수정된 프로필을 DTO로 조립하여 반환
      return profileDtoAssembler.toDto(profile);
  }

}