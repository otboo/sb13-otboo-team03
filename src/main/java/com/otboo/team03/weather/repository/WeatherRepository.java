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

  // 동일 좌표 및 동일 예보 대상 시각의 데이터 존재 여부 확인 (중복 적재 방지용)
  boolean existsByLatitudeAndLongitudeAndForecastAt(Double latitude, Double longitude, Instant forecastAt);

  // 동일 좌표 및 동일 예보 대상 시각의 기존 데이터 조회 (최신 예보 갱신용)
  Optional<Weather> findByLatitudeAndLongitudeAndForecastAt(Double latitude, Double longitude, Instant forecastAt);

  // 특정 좌표에서 예보 대상 시각 목록에 해당하는 기존 예보들을 한 번에 일괄 조회
  List<Weather> findAllByLatitudeAndLongitudeAndForecastAtIn(
      Double latitude, Double longitude, List<Instant> forecastAts
  );

}
