package com.otboo.team03.weather.repository;

import com.otboo.team03.weather.entity.Weather;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WeatherRepository extends JpaRepository<Weather, UUID> {
  // 특정 좌표에서 기준 시각(baseTime) 이하의 가장 최근 예보 단건 조회
  Optional<Weather> findTopByLatitudeAndLongitudeAndForecastAtLessThanEqualOrderByForecastAtDesc(
      Double latitude, Double longitude, Instant baseTime
  );

  // 특정 좌표의 가장 최근 예보 대상 날씨를 단건 조회
  Optional<Weather> findTopByLatitudeAndLongitudeOrderByForecastAtDesc(
      Double latitude, Double longitude
  );

  // 특정 좌표의 기간별 예보 목록 시간순 조회 (이후 날씨 조회 API용)
  List<Weather> findAllByLatitudeAndLongitudeAndForecastAtBetweenOrderByForecastAtAsc(
      Double latitude, Double longitude, Instant start, Instant end
  );
}
