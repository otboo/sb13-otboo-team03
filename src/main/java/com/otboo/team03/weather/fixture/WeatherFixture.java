package com.otboo.team03.weather.fixture;

import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.entity.Weather;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

// 팀원이 단위 및 통합 테스트에서 손쉽게 날씨 객체를 생성할 수 있도록 돕는 Fixture 클래스
public class WeatherFixture {

  public static final Double DEFAULT_LAT = 37.5665;
  public static final Double DEFAULT_LON = 126.9780;

  // 기본 평온한 날씨 (기온 22도, 맑음, 강수 없음)
  public static Weather createDefault() {
    return createCustom(22.0, SkyStatus.CLEAR, PrecipitationType.NONE);
  }

  // 무더운 여름 날씨 (기온 32도, 맑음, 강수 없음) - 반팔/반바지 추천 테스트용
  public static Weather createHotWeather() {
    return createCustom(32.0, SkyStatus.CLEAR, PrecipitationType.NONE);
  }

  // 쌀쌀하거나 추운 겨울 날씨 (기온 2도, 흐림, 강수 없음) - 아우터/패딩 추천 테스트용
  public static Weather createColdWeather() {
    return createCustom(2.0, SkyStatus.CLOUDY, PrecipitationType.NONE);
  }

  // 비 오는 날씨 (기온 18도, 흐림, 비, 강수량 15mm) - 우산/장화 추천 테스트용
  public static Weather createRainyWeather() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    return Weather.builder()
        .latitude(DEFAULT_LAT)
        .longitude(DEFAULT_LON)
        .forecastedAt(now.minus(1, ChronoUnit.HOURS))
        .forecastAt(now)
        .skyStatus(SkyStatus.CLOUDY)
        .precipitationType(PrecipitationType.RAIN)
        .precipitationAmount(15.0)
        .precipitationProbability(80.0)
        .temperature(18.0)
        .humidity(85.0)
        .windSpeed(4.5)
        .build();
  }

  // 커스텀 기온 및 날씨 상태 생성
  public static Weather createCustom(Double temperature, SkyStatus skyStatus, PrecipitationType precipitationType) {
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    return Weather.builder()
        .latitude(DEFAULT_LAT)
        .longitude(DEFAULT_LON)
        .forecastedAt(now.minus(1, ChronoUnit.HOURS))
        .forecastAt(now)
        .skyStatus(skyStatus)
        .precipitationType(precipitationType)
        .precipitationAmount(precipitationType == PrecipitationType.NONE ? 0.0 : 5.0)
        .precipitationProbability(precipitationType == PrecipitationType.NONE ? 0.0 : 60.0)
        .temperature(temperature)
        .humidity(50.0)
        .windSpeed(2.0)
        .build();
  }

}
