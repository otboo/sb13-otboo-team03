package com.otboo.team03.weather.service;

import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.Weather;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

// 날씨 예보 데이터를 분석하여 급격한 기상 변화를 감지하는 도메인 서비스
@Component
public class WeatherChangeDetector {
  // 기온 급변(급상승/급강하) 기준 임계치 (5.0℃)
  private static final double TEMPERATURE_RAPID_CHANGE_THRESHOLD = 5.0;

  // 예보 리스트를 분석하여 특별한 날씨 변화(강수 시작, 기온 급변 등)가 있는지 감지합니다.
  public Optional<WeatherAlertInfo> detect(List<Weather> weathers) {
    if (weathers == null || weathers.size() < 2) {
      return Optional.empty();
    }

    // 예보 시각순으로 정렬
    List<Weather> sortedWeathers = weathers.stream()
        .sorted(Comparator.comparing(Weather::getForecastAt))
        .toList();

    // 1. 강수(비, 눈 등) 시작 감지 (중복 방지: 직전 예보가 강수 없음(NONE)이고, 향후 강수가 발생하는 시점 감지)
    Weather current = sortedWeathers.get(0);
    if (current.getPrecipitationType() == PrecipitationType.NONE) {
      for (int i = 1; i < sortedWeathers.size(); i++) {
        Weather future = sortedWeathers.get(i);
        if (future.getPrecipitationType() != PrecipitationType.NONE) {
          return Optional.of(new WeatherAlertInfo(
              "비/눈 예보 알림",
              "향후 비 또는 눈 예보가 있습니다. 외출 시 우산을 챙기세요!"
          ));
        }
      }
    }

    // 2. 기온 급상승 감지 (직전 예보 대비 5.0℃ 이상 급격히 상승하는 경우)
    for (int i = 1; i < sortedWeathers.size(); i++) {
      Weather prev = sortedWeathers.get(i - 1);
      Weather next = sortedWeathers.get(i);
      double tempDiff = next.getTemperature() - prev.getTemperature();

      // (1) 기온 급상승 (+5.0℃ 이상)
      if (tempDiff >= TEMPERATURE_RAPID_CHANGE_THRESHOLD) {
        return Optional.of(new WeatherAlertInfo(
            "기온 급상승 알림",
            String.format("기온이 급격히 상승할 예정입니다(약 +%.1f℃). 옷차림에 유의하세요!", tempDiff)
        ));
      }

      // (2) 기온 급강하 (-5.0℃ 이하)
      if (tempDiff <= -TEMPERATURE_RAPID_CHANGE_THRESHOLD) {
        return Optional.of(new WeatherAlertInfo(
            "기온 급강하 알림",
            String.format("기온이 급격히 떨어질 예정입니다(약 %.1f℃). 따뜻한 외투를 챙기세요!", tempDiff)
        ));
      }
    }

    return Optional.empty();
  }

}