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
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    // 1. 이미 날씨 데이터가 존재하면 중복 생성하지 않고 종료
    if (weatherRepository.count() > 0) {
      return;
    }

    // 2. 현재 시각 기준으로 오늘/내일 3시간 간격 예보 8건(24시간 분량) 생성
    Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);
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

    // 3. 일괄 저장
    weatherRepository.saveAll(mockWeathers);
  }

}