package com.otboo.team03.weather.entity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 하늘 상태 열거형
@Getter
@RequiredArgsConstructor
public enum SkyStatus {
  CLEAR("맑음"),
  MOSTLY_CLOUDY("구름많음"),
  CLOUDY("흐림");
  private final String description;
}