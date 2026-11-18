package com.otboo.team03.weather.client;

import com.otboo.team03.weather.client.dto.OpenWeatherForecastResponse;
import com.otboo.team03.weather.client.dto.OpenWeatherForecastResponse.ForecastItem;
import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.entity.Weather;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OpenWeatherMapper {

  // OpenWeatherMap 응답 객체를 List<Weather> 엔티티 목록으로 변환
  public List<Weather> toWeatherEntities(OpenWeatherForecastResponse response, Double reqLat, Double reqLon) {
    if (response == null || response.list() == null || response.list().isEmpty()) {
      return Collections.emptyList();
    }

    Instant now = Instant.now();

    return response.list().stream()
        .map(item -> toEntity(item, reqLat, reqLon, now))
        .toList();
  }

  private Weather toEntity(ForecastItem item, Double latitude, Double longitude, Instant forecastedAt) {
    Instant forecastAt = Instant.ofEpochSecond(item.dt());

    // Weather Condition Code (id) 기반 하늘상태 및 강수형태 추론
    int conditionCode = (item.weather() != null && !item.weather().isEmpty())
        ? item.weather().get(0).id()
        : 800; // 기본 맑음

    SkyStatus skyStatus = resolveSkyStatus(conditionCode);
    PrecipitationType precipitationType = resolvePrecipitationType(conditionCode);

    // 강수 확률 (% 단위: 0.0 ~ 1.0 -> 0 ~ 100%)
    double pop = (item.pop() != null) ? Math.round(item.pop() * 100.0) : 0.0;

    // 강수량 산출 (비 또는 눈 수치)
    double amount = 0.0;
    if (item.rain() != null && item.rain().threeHour() != null) {
      amount = item.rain().threeHour();
    } else if (item.snow() != null && item.snow().threeHour() != null) {
      amount = item.snow().threeHour();
    }

    double temp = (item.main() != null && item.main().temp() != null) ? item.main().temp() : 0.0;
    double humidity = (item.main() != null && item.main().humidity() != null) ? item.main().humidity() : 0.0;
    double windSpeed = (item.wind() != null && item.wind().speed() != null) ? item.wind().speed() : 0.0;

    return Weather.builder()
        .latitude(latitude)
        .longitude(longitude)
        .forecastedAt(forecastedAt)
        .forecastAt(forecastAt)
        .skyStatus(skyStatus)
        .precipitationType(precipitationType)
        .precipitationAmount(amount)
        .precipitationProbability(pop)
        .temperature(temp)
        .humidity(humidity)
        .windSpeed(windSpeed)
        .build();
  }

  // Weather Condition Code 기반 하늘상태(SkyStatus) 추론
  private SkyStatus resolveSkyStatus(int code) {
    if (code == 800) {
      return SkyStatus.CLEAR; // 맑음
    } else if (code == 801 || code == 802) {
      return SkyStatus.MOSTLY_CLOUDY; // 구름많음
    } else {
      // 803, 804(흐림) 및 비/눈/뇌우/안개 등 모든 악천후
      return SkyStatus.CLOUDY; // 흐림
    }
  }

  // Weather Condition Code 기반 강수형태(PrecipitationType) 추론
  private PrecipitationType resolvePrecipitationType(int code) {
    if (code >= 200 && code < 300) {
      return PrecipitationType.RAIN; // 뇌우 (비)
    } else if (code >= 300 && code < 400) {
      return PrecipitationType.RAIN; // 이슬비
    } else if (code >= 500 && code < 600) {
      if (code == 511) return PrecipitationType.RAIN_SNOW; // 어는 비
      if (code >= 520) return PrecipitationType.SHOWER;    // 소나기
      return PrecipitationType.RAIN;                      // 일반 비
    } else if (code >= 600 && code < 700) {
      if (code >= 611 && code <= 616) return PrecipitationType.RAIN_SNOW; // 진눈깨비
      return PrecipitationType.SNOW; // 눈
    }
    return PrecipitationType.NONE; // 강수 없음 (7xx, 8xx)
  }

}