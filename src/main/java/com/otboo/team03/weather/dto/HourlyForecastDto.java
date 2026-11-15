package com.otboo.team03.weather.dto;

import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.entity.Weather;
import java.time.Instant;
import java.util.UUID;

// 시간때별 예보 1건을 담는 Dto
public record HourlyForecastDto(
    UUID weatherId,
    Instant forecastAt,
    Double temperature,
    SkyStatus skyStatus,
    PrecipitationType precipitationType,
    Double precipitationAmount,
    Double precipitationProbability,
    Double humidity,
    Double windSpeed
) {
  public static HourlyForecastDto from(Weather weather) {
    return new HourlyForecastDto(
        weather.getId(),
        weather.getForecastAt(),
        weather.getTemperature(),
        weather.getSkyStatus(),
        weather.getPrecipitationType(),
        weather.getPrecipitationAmount(),
        weather.getPrecipitationProbability(),
        weather.getHumidity(),
        weather.getWindSpeed()
    );
  }
}
