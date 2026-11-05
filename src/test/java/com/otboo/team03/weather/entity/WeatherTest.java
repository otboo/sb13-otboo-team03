package com.otboo.team03.weather.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.weather.exception.WeatherNotFoundException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WeatherTest {

  @Test
  @DisplayName("날씨 엔티티가 정상적으로 생성되고 모든 Getter가 올바른 값을 반환한다")
  void createWeather_success() {
    // given
    Instant now = Instant.now();
    Instant forecastTime = now.plusSeconds(3600);

    // when
    Weather weather = Weather.builder()
        .latitude(37.5665)
        .longitude(126.9780)
        .forecastedAt(now)
        .forecastAt(forecastTime)
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(PrecipitationType.NONE)
        .precipitationAmount(0.0)
        .precipitationProbability(10.0)
        .temperature(21.5)
        .humidity(55.0)
        .windSpeed(2.3)
        .build();

    // then
    assertThat(weather.getLatitude()).isEqualTo(37.5665);
    assertThat(weather.getLongitude()).isEqualTo(126.9780);
    assertThat(weather.getForecastedAt()).isEqualTo(now);
    assertThat(weather.getForecastAt()).isEqualTo(forecastTime);
    assertThat(weather.getSkyStatus()).isEqualTo(SkyStatus.CLEAR);
    assertThat(weather.getPrecipitationType()).isEqualTo(PrecipitationType.NONE);
    assertThat(weather.getPrecipitationAmount()).isEqualTo(0.0);
    assertThat(weather.getPrecipitationProbability()).isEqualTo(10.0);
    assertThat(weather.getTemperature()).isEqualTo(21.5);
    assertThat(weather.getHumidity()).isEqualTo(55.0);
    assertThat(weather.getWindSpeed()).isEqualTo(2.3);
  }

  @Test
  @DisplayName("JPA 기본 생성자 및 빌더 toString 메서드가 정상 동작한다")
  void defaultConstructorAndBuilderToString() {
    // 동일 패키지 내 protected no-arg 생성자 호출 검증 (JPA 프록시 스펙 커버)
    Weather weather = new Weather();
    assertThat(weather).isNotNull();
    // Lombok 빌더 내부 toString() 호출 검증
    String builderString = Weather.builder().toString();
    assertThat(builderString).isNotNull();
  }

  @Nested
  @DisplayName("날씨 엔티티 생성 시 필수 필드 누락 검증")
  class RequireNonNullTest {

    private final Instant now = Instant.now();

    @Test
    @DisplayName("latitude가 null이면 NullPointerException이 발생한다")
    void latitudeNull() {
      assertThatThrownBy(() -> Weather.builder()
          .latitude(null).longitude(126.9780).forecastedAt(now).forecastAt(now)
          .skyStatus(SkyStatus.CLEAR).precipitationType(PrecipitationType.NONE)
          .precipitationAmount(0.0).precipitationProbability(0.0).temperature(20.0).humidity(50.0).windSpeed(1.0)
          .build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("latitude");
    }

    @Test
    @DisplayName("longitude가 null이면 NullPointerException이 발생한다")
    void longitudeNull() {
      assertThatThrownBy(() -> Weather.builder()
          .latitude(37.5665).longitude(null).forecastedAt(now).forecastAt(now)
          .skyStatus(SkyStatus.CLEAR).precipitationType(PrecipitationType.NONE)
          .precipitationAmount(0.0).precipitationProbability(0.0).temperature(20.0).humidity(50.0).windSpeed(1.0)
          .build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("longitude");
    }

    @Test
    @DisplayName("forecastedAt이 null이면 NullPointerException이 발생한다")
    void forecastedAtNull() {
      assertThatThrownBy(() -> Weather.builder()
          .latitude(37.5665).longitude(126.9780).forecastedAt(null).forecastAt(now)
          .skyStatus(SkyStatus.CLEAR).precipitationType(PrecipitationType.NONE)
          .precipitationAmount(0.0).precipitationProbability(0.0).temperature(20.0).humidity(50.0).windSpeed(1.0)
          .build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("forecastedAt");
    }

    @Test
    @DisplayName("forecastAt이 null이면 NullPointerException이 발생한다")
    void forecastAtNull() {
      assertThatThrownBy(() -> Weather.builder()
          .latitude(37.5665).longitude(126.9780).forecastedAt(now).forecastAt(null)
          .skyStatus(SkyStatus.CLEAR).precipitationType(PrecipitationType.NONE)
          .precipitationAmount(0.0).precipitationProbability(0.0).temperature(20.0).humidity(50.0).windSpeed(1.0)
          .build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("forecastAt");
    }

    @Test
    @DisplayName("skyStatus가 null이면 NullPointerException이 발생한다")
    void skyStatusNull() {
      assertThatThrownBy(() -> Weather.builder()
          .latitude(37.5665).longitude(126.9780).forecastedAt(now).forecastAt(now)
          .skyStatus(null).precipitationType(PrecipitationType.NONE)
          .precipitationAmount(0.0).precipitationProbability(0.0).temperature(20.0).humidity(50.0).windSpeed(1.0)
          .build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("skyStatus");
    }

    @Test
    @DisplayName("precipitationType이 null이면 NullPointerException이 발생한다")
    void precipitationTypeNull() {
      assertThatThrownBy(() -> Weather.builder()
          .latitude(37.5665).longitude(126.9780).forecastedAt(now).forecastAt(now)
          .skyStatus(SkyStatus.CLEAR).precipitationType(null)
          .precipitationAmount(0.0).precipitationProbability(0.0).temperature(20.0).humidity(50.0).windSpeed(1.0)
          .build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("precipitationType");
    }

    @Test
    @DisplayName("precipitationAmount가 null이면 NullPointerException이 발생한다")
    void precipitationAmountNull() {
      assertThatThrownBy(() -> Weather.builder()
          .latitude(37.5665).longitude(126.9780).forecastedAt(now).forecastAt(now)
          .skyStatus(SkyStatus.CLEAR).precipitationType(PrecipitationType.NONE)
          .precipitationAmount(null).precipitationProbability(0.0).temperature(20.0).humidity(50.0).windSpeed(1.0)
          .build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("precipitationAmount");
    }

    @Test
    @DisplayName("precipitationProbability가 null이면 NullPointerException이 발생한다")
    void precipitationProbabilityNull() {
      assertThatThrownBy(() -> Weather.builder()
          .latitude(37.5665).longitude(126.9780).forecastedAt(now).forecastAt(now)
          .skyStatus(SkyStatus.CLEAR).precipitationType(PrecipitationType.NONE)
          .precipitationAmount(0.0).precipitationProbability(null).temperature(20.0).humidity(50.0).windSpeed(1.0)
          .build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("precipitationProbability");
    }

    @Test
    @DisplayName("temperature가 null이면 NullPointerException이 발생한다")
    void temperatureNull() {
      assertThatThrownBy(() -> Weather.builder()
          .latitude(37.5665).longitude(126.9780).forecastedAt(now).forecastAt(now)
          .skyStatus(SkyStatus.CLEAR).precipitationType(PrecipitationType.NONE)
          .precipitationAmount(0.0).precipitationProbability(0.0).temperature(null).humidity(50.0).windSpeed(1.0)
          .build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("temperature");
    }

    @Test
    @DisplayName("humidity가 null이면 NullPointerException이 발생한다")
    void humidityNull() {
      assertThatThrownBy(() -> Weather.builder()
          .latitude(37.5665).longitude(126.9780).forecastedAt(now).forecastAt(now)
          .skyStatus(SkyStatus.CLEAR).precipitationType(PrecipitationType.NONE)
          .precipitationAmount(0.0).precipitationProbability(0.0).temperature(20.0).humidity(null).windSpeed(1.0)
          .build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("humidity");
    }

    @Test
    @DisplayName("windSpeed가 null이면 NullPointerException이 발생한다")
    void windSpeedNull() {
      assertThatThrownBy(() -> Weather.builder()
          .latitude(37.5665).longitude(126.9780).forecastedAt(now).forecastAt(now)
          .skyStatus(SkyStatus.CLEAR).precipitationType(PrecipitationType.NONE)
          .precipitationAmount(0.0).precipitationProbability(0.0).temperature(20.0).humidity(50.0).windSpeed(null)
          .build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("windSpeed");
    }
  }

  @Test
  @DisplayName("열거형 및 커스텀 예외가 정상 동작한다")
  void enumAndExceptionTest() {
    // SkyStatus 열거형 검증
    assertThat(SkyStatus.values()).contains(SkyStatus.CLEAR, SkyStatus.MOSTLY_CLOUDY, SkyStatus.CLOUDY);
    assertThat(SkyStatus.valueOf("CLEAR")).isEqualTo(SkyStatus.CLEAR);
    assertThat(SkyStatus.CLEAR.getDescription()).isEqualTo("맑음");
    assertThat(SkyStatus.MOSTLY_CLOUDY.getDescription()).isEqualTo("구름많음");
    assertThat(SkyStatus.CLOUDY.getDescription()).isEqualTo("흐림");

    // PrecipitationType 열거형 검증
    assertThat(PrecipitationType.values()).contains(
        PrecipitationType.NONE, PrecipitationType.RAIN, PrecipitationType.RAIN_SNOW, PrecipitationType.SNOW, PrecipitationType.SHOWER
    );
    assertThat(PrecipitationType.valueOf("NONE")).isEqualTo(PrecipitationType.NONE);
    assertThat(PrecipitationType.NONE.getDescription()).isEqualTo("없음");
    assertThat(PrecipitationType.RAIN.getDescription()).isEqualTo("비");
    assertThat(PrecipitationType.RAIN_SNOW.getDescription()).isEqualTo("비/눈");
    assertThat(PrecipitationType.SNOW.getDescription()).isEqualTo("눈");
    assertThat(PrecipitationType.SHOWER.getDescription()).isEqualTo("소나기");

    // WeatherNotFoundException 및 ErrorCode 검증
    WeatherNotFoundException exception = new WeatherNotFoundException();
    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND_WEATHER);
    assertThat(exception.getErrorCode().getMessage()).isEqualTo("날씨 정보가 존재하지 않습니다.");
  }

}