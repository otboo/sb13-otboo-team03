package com.otboo.team03.profile.repository;

import com.otboo.team03.profile.entity.Profile;
import com.otboo.team03.profile.dto.ProfileTemperatureSensitivityDto;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {
  // 특정 거점 좌표의 사용자 프로필 목록 조회 (날씨 급변 알림 발송 대상 추출용)
  List<Profile> findAllByLatitudeAndLongitude(Double latitude, Double longitude);

  @Query("""
      select new com.otboo.team03.profile.dto.ProfileTemperatureSensitivityDto(p.temperatureSensitivity)
      from Profile p
      where p.userId = :userId
      """)
  Optional<ProfileTemperatureSensitivityDto> findTemperatureSensitivityByUserId(@Param("userId") UUID userId);
}
