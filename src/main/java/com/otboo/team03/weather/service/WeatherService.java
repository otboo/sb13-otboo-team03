package com.otboo.team03.weather.service;

import com.otboo.team03.weather.dto.WeatherResponse;

public interface WeatherService {

  // 좌표(위도, 경도)를 받아 현재 날씨와 예보 목록을 반환함.
  WeatherResponse getWeather(Double latitude, Double longitude);

}