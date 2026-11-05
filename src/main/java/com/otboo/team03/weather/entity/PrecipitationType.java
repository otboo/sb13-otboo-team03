package com.otboo.team03.weather.entity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 강수 형태 열거형
@Getter
@RequiredArgsConstructor
public enum PrecipitationType {
  NONE("없음"),
  RAIN("비"),
  RAIN_SNOW("비/눈"),
  SNOW("눈"),
  SHOWER("소나기");
  private final String description;
}