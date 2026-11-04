package com.otboo.team03.profile.mapper;

import com.otboo.team03.global.storage.ImageStorage;
import com.otboo.team03.profile.dto.ProfileDto;
import com.otboo.team03.profile.entity.Profile;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileDtoAssembler {

  private final Optional<ImageStorage> imageStorage;

  private String resolveImageUrl(String raw) {
    // 1. null이면 null
    if (raw == null) {
      return null;
    }
    // 2. http:// 나 https:// 로 시작하면 기존 외부 URL이므로 그대로 반환
    if (raw.startsWith("http://") || raw.startsWith("https://")) {
      return raw;
    }
    // 3. 그 외(S3 Key 형태)면 ImageStorage에서 서명 URL 발급 (빈이 없으면 null)
    return imageStorage.map(storage -> storage.getAccessUrl(raw)).orElse(null);
  }

  public ProfileDto toDto(Profile profile) {
    String accessUrl = resolveImageUrl(profile.getProfileImageUrl());
    return ProfileDto.from(profile, accessUrl);
  }

}
