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
    return from(weather, location, weather.getTemperature(), weather.getTemperature(), 0.0);
  }

  // 계산된 최저/최고/어제대비 기온을 반영하는 오버로딩 메서드
  public static WeatherDto from(
      Weather weather,
      WeatherAPILocation location,
      Double minTemp,
      Double maxTemp,
      Double tempDiff
  ) {
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
            0.0
        ),
        new TemperatureDto(
            weather.getTemperature(), // 현재 예보 시점 기온
            tempDiff,                 // 계산된 전날 대비 기온 변동
            minTemp,                  // 계산된 일 최저 기온
            maxTemp                   // 계산된 일 최고 기온
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