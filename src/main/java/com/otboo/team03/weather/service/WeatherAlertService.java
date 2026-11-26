package com.otboo.team03.weather.service;

import com.otboo.team03.weather.entity.Weather;
import java.util.List;

public interface WeatherAlertService {
  // 특정 거점 좌표의 날씨 예보를 분석하여 급변 감지 시 해당 거점 유저들에게 알림 발행
  void checkAndSendAlerts(Double latitude, Double longitude, List<Weather> weathers);

}