package com.otboo.team03.weather.dto;

import java.util.List;

public record WeatherResponse(
    HourlyForecastDto currentWeather,
    Double minTemperature,          // 당일 최저 기온
    Double maxTemperature,          // 당일 최고 기온
    Double temperatureDifference,    // 어제 대비 기온 차이 (+2.5, -1.0 등)
    List<HourlyForecastDto> hourlyForecasts
) {
  public static WeatherResponse of(
      HourlyForecastDto current,
      Double minTemp,
      Double maxTemp,
      Double tempDiff,
      List<HourlyForecastDto> forecasts
  ) {
    return new WeatherResponse(current, minTemp, maxTemp, tempDiff, forecasts);
  }
}