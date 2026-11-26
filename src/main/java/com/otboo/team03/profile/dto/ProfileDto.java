package com.otboo.team03.profile.dto;

import com.otboo.team03.profile.entity.Profile;
import java.time.LocalDate;
import java.util.UUID;

public record ProfileDto(
    UUID userId,
    String name,
    Gender gender,
    LocalDate birthDate,
    WeatherAPILocation location,
    Integer temperatureSensitivity,
    String profileImageUrl
) {
  // Profile 엔티티를 DTO로 변환해 주는 정적 팩토리 메서드
  public static ProfileDto from(Profile profile, String profileImageUrl) {
    boolean hasNoLocationNames = profile.getLocationNames() == null || profile.getLocationNames().isEmpty();
    boolean hasNoCoordinates = profile.getLatitude() == null && profile.getLongitude() == null;

    WeatherAPILocation location = (hasNoLocationNames || hasNoCoordinates)
        ? null
        : new WeatherAPILocation(
        profile.getLatitude(),
        profile.getLongitude(),
        null, // 기상청 x좌표 (기상청 좌표 변환 기능 연동 시 확장)
        null,// 기상청 y좌표
        profile.getLocationNames()
    );
    return new ProfileDto(
        profile.getUserId(),
        profile.getUser() != null ? profile.getUser().getName() : null,
        profile.getGender(),
        profile.getBirthDate(),
        location,
        profile.getTemperatureSensitivity(),
        profileImageUrl
    );
  }

}