package com.otboo.team03.weather.init;

import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.repository.WeatherRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherInitializer implements ApplicationRunner {

  private final WeatherRepository weatherRepository;

  // 강남 좌표
  private static final Double DEFAULT_LATITUDE = 37.4979;
  private static final Double DEFAULT_LONGITUDE = 127.0276;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    initialize();
  }

  public void initialize() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);

    // 1. 현재 시각 이후의 유효 예보 데이터가 이미 존재하면 중복 생성 없이 종료
    if (weatherRepository.existsByForecastAtAfter(now)) {
      log.info(">>>>> [WeatherInitializer] 현재 시각 이후의 유효 날씨 데이터가 이미 존재하므로 초기화를 건너뜁니다.");
      return;
    }

    // 2. 현재 시각 기준으로 오늘/내일 3시간 간격 예보 8건(24시간 분량) 생성
    List<Weather> mockWeathers = new ArrayList<>();

    for (int i = 0; i < 8; i++) {
      Instant forecastAt = now.plus(i * 3L, ChronoUnit.HOURS);

      Weather weather = Weather.builder()
          .latitude(DEFAULT_LATITUDE)
          .longitude(DEFAULT_LONGITUDE)
          .forecastedAt(now)
          .forecastAt(forecastAt)
          .skyStatus(SkyStatus.CLEAR)
          .precipitationType(PrecipitationType.NONE)
          .precipitationAmount(0.0)
          .precipitationProbability(0.0)
          .temperature(18.0 + (i % 4) * 2.0) // 18℃ ~ 24℃ 사이의 자연스러운 기온 변화
          .humidity(50.0 + (i % 3) * 5.0)    // 50% ~ 60% 습도
          .windSpeed(2.5)
          .build();

      mockWeathers.add(weather);
    }

    weatherRepository.saveAll(mockWeathers);
    log.info(">>>>> [WeatherInitializer] 오늘 기준 24시간 더미 날씨 데이터 8건 초기화 완료");
  }

}