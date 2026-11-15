package com.otboo.team03.weather.dto;

import java.util.List;

// 현재 날씨+시간대별 예보 목록 전체 응답DTO
public record WeatherResponse(
    HourlyForecastDto currentWeather,
    List<HourlyForecastDto> hourlyForecasts
) {
  public static WeatherResponse of(HourlyForecastDto current, List<HourlyForecastDto> forecasts) {
    return new WeatherResponse(current, forecasts);
  }
}
