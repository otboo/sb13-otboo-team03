package com.otboo.team03.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.entity.Weather;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeatherChangeDetectorTest {

  private final WeatherChangeDetector detector = new WeatherChangeDetector();

  private Weather createWeather(Instant forecastAt, SkyStatus sky, PrecipitationType precip, Double temp) {
    return Weather.builder()
        .latitude(37.5665)
        .longitude(126.9780)
        .forecastedAt(Instant.now())
        .forecastAt(forecastAt)
        .skyStatus(sky)
        .precipitationType(precip)
        .precipitationAmount(precip == PrecipitationType.NONE ? 0.0 : 5.0)
        .precipitationProbability(precip == PrecipitationType.NONE ? 0.0 : 80.0)
        .temperature(temp)
        .humidity(60.0)
        .windSpeed(2.0)
        .build();
  }

  @Test
  @DisplayName("향후 예보에 비/눈 등의 강수가 발생하면 강수 알림 정보를 반환한다")
  void detect_whenPrecipitationOccurs_returnsPrecipitationAlert() {
    // given
    Instant now = Instant.parse("2026-11-23T06:00:00Z");
    Weather current = createWeather(now, SkyStatus.CLEAR, PrecipitationType.NONE, 15.0);
    Weather futureRain = createWeather(now.plusSeconds(10800), SkyStatus.CLOUDY, PrecipitationType.RAIN, 14.0);

    // when
    Optional<WeatherAlertInfo> alertOpt = detector.detect(List.of(current, futureRain));

    // then
    assertThat(alertOpt).isPresent();
    assertThat(alertOpt.get().title()).contains("비/눈 예보 알림");
    assertThat(alertOpt.get().content()).contains("우산을 챙기세요");
  }

  @Test
  @DisplayName("직전 예보 대비 기온이 5.0도 이상 급격히 상승하면 기온 급상승 알림 정보를 반환한다")
  void detect_whenTemperatureRisesRapidly_returnsTemperatureAlert() {
    // given
    Instant now = Instant.parse("2026-11-23T06:00:00Z");
    Weather current = createWeather(now, SkyStatus.CLEAR, PrecipitationType.NONE, 10.0);
    // 3시간 뒤 기온이 10.0도에서 16.0도로 6도 급상승
    Weather futureHot = createWeather(now.plusSeconds(10800), SkyStatus.CLEAR, PrecipitationType.NONE, 16.0);

    // when
    Optional<WeatherAlertInfo> alertOpt = detector.detect(List.of(current, futureHot));

    // then
    assertThat(alertOpt).isPresent();
    assertThat(alertOpt.get().title()).contains("기온 급변 알림");
    assertThat(alertOpt.get().content()).contains("기온이 급격히 상승");
  }

  @Test
  @DisplayName("특별한 날씨 변화(강수 없음, 기온 완만한 변화)가 없으면 알림을 반환하지 않는다")
  void detect_whenNoSignificantChange_returnsEmpty() {
    // given
    Instant now = Instant.parse("2026-11-23T06:00:00Z");
    Weather current = createWeather(now, SkyStatus.CLEAR, PrecipitationType.NONE, 15.0);
    Weather future = createWeather(now.plusSeconds(10800), SkyStatus.CLEAR, PrecipitationType.NONE, 16.5);

    // when
    Optional<WeatherAlertInfo> alertOpt = detector.detect(List.of(current, future));

    // then
    assertThat(alertOpt).isEmpty();
  }

  @Test
  @DisplayName("예보 리스트가 null이거나 1건 이하이면 알림을 반환하지 않는다 (방어 검증)")
  void detect_whenListIsNullOrEmpty_returnsEmpty() {
    assertThat(detector.detect(null)).isEmpty();
    assertThat(detector.detect(List.of())).isEmpty();
    assertThat(detector.detect(List.of(
        createWeather(Instant.now(), SkyStatus.CLEAR, PrecipitationType.NONE, 15.0)
    ))).isEmpty();
  }


}