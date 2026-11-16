package com.otboo.team03.weather.dto;

import static org.assertj.core.api.Assertions.assertThat;
import com.otboo.team03.profile.dto.WeatherAPILocation;
import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.fixture.WeatherFixture;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class WeatherDtoTest {

  @Test
  @DisplayName("Weather 엔티티와 WeatherAPILocation을 넘기면 모든 DTO 필드가 정상 조립된다")
  void from_success() {
    // given
    UUID id = UUID.randomUUID();
    Weather weather = WeatherFixture.createRainyWeather();
    ReflectionTestUtils.setField(weather, "id", id);
    WeatherAPILocation location = new WeatherAPILocation(
        37.5665, 126.9780, 60, 127, List.of("서울특별시", "중구", "명동")
    );
    // when
    WeatherDto dto = WeatherDto.from(weather, location);
    // then
    assertThat(dto.id()).isEqualTo(id);
    assertThat(dto.forecastAt()).isEqualTo(weather.getForecastAt());
    assertThat(dto.forecastedAt()).isEqualTo(weather.getForecastedAt());
    assertThat(dto.location()).isEqualTo(location);
    assertThat(dto.skyStatus()).isEqualTo(SkyStatus.CLOUDY);
    // PrecipitationDto 검증
    assertThat(dto.precipitation().type()).isEqualTo(PrecipitationType.RAIN);
    assertThat(dto.precipitation().amount()).isEqualTo(15.0);
    assertThat(dto.precipitation().probability()).isEqualTo(80.0);
    // HumidityDto 검증
    assertThat(dto.humidity().current()).isEqualTo(85.0);
    assertThat(dto.humidity().comparedToDayBefore()).isEqualTo(0.0);
    // TemperatureDto 검증
    assertThat(dto.temperature().current()).isEqualTo(18.0);
    assertThat(dto.temperature().comparedToDayBefore()).isEqualTo(0.0);
    assertThat(dto.temperature().min()).isEqualTo(18.0);
    assertThat(dto.temperature().max()).isEqualTo(18.0);
    // WindSpeedDto 검증
    assertThat(dto.windSpeed().speed()).isEqualTo(4.5);
    assertThat(dto.windSpeed().asWord()).isEqualTo("MODERATE");
  }

  @ParameterizedTest
  @CsvSource(value = {
      "null, WEAK",
      "2.0, WEAK",
      "6.0, MODERATE",
      "12.0, STRONG"
  }, nullValues = {"null"})

  @DisplayName("풍속 수치에 따라 정성 정보(WEAK, MODERATE, STRONG)가 올바르게 분류된다")
  void resolveWindWord_branches(Double speed, String expectedWord) {
    // given
    Weather weather = Weather.builder()
        .latitude(37.5665)
        .longitude(126.9780)
        .forecastedAt(Instant.now())
        .forecastAt(Instant.now())
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(PrecipitationType.NONE)
        .precipitationAmount(0.0)
        .precipitationProbability(0.0)
        .temperature(20.0)
        .humidity(50.0)
        .windSpeed(speed == null ? 0.0 : speed)
        .build();
    if (speed == null) {
      ReflectionTestUtils.setField(weather, "windSpeed", null);
    }
    WeatherAPILocation location = new WeatherAPILocation(37.5665, 126.9780, 60, 127, List.of("서울"));
    // when
    WeatherDto dto = WeatherDto.from(weather, location);
    // then
    assertThat(dto.windSpeed().asWord()).isEqualTo(expectedWord);
  }

}