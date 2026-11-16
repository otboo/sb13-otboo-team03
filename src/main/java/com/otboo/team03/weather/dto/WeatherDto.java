package com.otboo.team03.weather.dto;

import com.otboo.team03.profile.dto.WeatherAPILocation;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.entity.Weather;
import java.time.Instant;
import java.util.UUID;

public record WeatherDto(
    UUID id,
    Instant forecastedAt,
    Instant forecastAt,
    WeatherAPILocation location,
    SkyStatus skyStatus,
    PrecipitationDto precipitation,
    HumidityDto humidity,
    TemperatureDto temperature,
    WindSpeedDto windSpeed
) {
  // Weather 엔티티로부터 DTO를 조립하는 정적 팩토리 메서드
  public static WeatherDto from(Weather weather, WeatherAPILocation location) {
    return new WeatherDto(
        weather.getId(),
        weather.getForecastedAt(),
        weather.getForecastAt(),
        location,
        weather.getSkyStatus(),
        new PrecipitationDto(
            weather.getPrecipitationType(),
            weather.getPrecipitationAmount(),
            weather.getPrecipitationProbability()
        ),
        new HumidityDto(
            weather.getHumidity(),
            0.0 // T-033 전날 대비 집계 로직 전 기본값
        ),
        new TemperatureDto(
            weather.getTemperature(),
            0.0, // 전날 대비 기온 변동
            weather.getTemperature(), // 일 최저 기온
            weather.getTemperature()  // 일 최고 기온
        ),
        new WindSpeedDto(
            weather.getWindSpeed(),
            resolveWindWord(weather.getWindSpeed())
        )
    );
  }

  private static String resolveWindWord(Double speed) {
    if (speed == null) return "WEAK";
    if (speed < 4.0) return "WEAK";
    if (speed < 9.0) return "MODERATE";
    return "STRONG";
  }

}