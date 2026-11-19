package com.otboo.team03.weather.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OpenWeatherForecastResponse(
    String cod,
    Integer message,
    Integer cnt,
    List<ForecastItem> list,
    City city
) {
  public record ForecastItem(
      Long dt, // 예보 대상 시각 (Unix Timestamp 초 단위)
      Main main,
      List<WeatherCondition> weather,
      Wind wind,
      Double pop, // 강수 확률 (0.0 ~ 1.0)
      Rain rain,
      Snow snow
  ) {}

  public record Main(
      Double temp,       // 기온 (℃)
      Double humidity    // 습도 (%)
  ) {}

  public record WeatherCondition(
      Integer id,         // 기상 상태 코드 (Weather Condition Code: 200~804)
      String main,
      String description
  ) {}

  public record Wind(
      Double speed        // 풍속 (m/s)
  ) {}

  public record Rain(
      @JsonProperty("3h") Double threeHour // 3시간 강수량 (mm)
  ) {}

  public record Snow(
      @JsonProperty("3h") Double threeHour // 3시간 적설량 (mm)
  ) {}

  public record City(
      Coord coord
  ) {}

  public record Coord(
      Double lat,
      Double lon
  ) {}

}