package com.otboo.team03.weather.dto;

import com.otboo.team03.profile.dto.WeatherAPILocation;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.entity.Weather;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "날씨 정보")
public record WeatherDto(
    @Schema(description = "날씨 ID") UUID id,
    @Schema(description = "예보된 시간") Instant forecastedAt,
    @Schema(description = "예보 대상 시간") Instant forecastAt,
    @Schema(description = "위치") WeatherAPILocation location,
    @Schema(description = "하늘 상태") SkyStatus skyStatus,
    @Schema(description = "강수량") PrecipitationDto precipitation,
    @Schema(description = "습도") HumidityDto humidity,
    @Schema(description = "온도") TemperatureDto temperature,
    @Schema(description = "풍속") WindSpeedDto windSpeed
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