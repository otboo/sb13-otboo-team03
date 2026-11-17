package com.otboo.team03.weather.util;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TemperatureCalculator {
  // 1. 최저 기온 계산
  public Double calculateMin(List<Double> temperatures) {
    if (temperatures == null || temperatures.isEmpty()) {
      return null;
    }
    return temperatures.stream().min(Double::compareTo).orElse(null);
  }

  // 2. 최고 기온 계산
  public Double calculateMax(List<Double> temperatures) {
    if (temperatures == null || temperatures.isEmpty()) {
      return null;
    }
    return temperatures.stream().max(Double::compareTo).orElse(null);
  }

  // 3. 어제 대비 기온 차이 계산 (현재 기온 - 어제 기온)
  public Double calculateDifference(Double currentTemp, Double yesterdayTemp) {
    // currentTemp 또는 yesterdayTemp가 null이면 null을 반환하도록 함.
    if (currentTemp == null || yesterdayTemp == null) {
      return null;
    }

    // (currentTemp - yesterdayTemp)를 계산하고, 소수점 1자리로 반올림하여 반환함.
    double diff = currentTemp - yesterdayTemp;
    return Math.round(diff * 10.0) / 10.0;
  }


}