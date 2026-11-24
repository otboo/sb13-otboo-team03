package com.otboo.team03.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.otboo.team03.profile.dto.WeatherAPILocation;
import com.otboo.team03.weather.client.OpenWeatherClient;
import com.otboo.team03.weather.dto.WeatherDto;
import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.exception.WeatherNotFoundException;
import com.otboo.team03.weather.fixture.WeatherFixture;
import com.otboo.team03.weather.repository.WeatherRepository;
import com.otboo.team03.weather.util.TemperatureCalculator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

  @Mock
  private LocationService locationService;

  @Mock
  private WeatherRepository weatherRepository;

  @Mock
  private TemperatureCalculator temperatureCalculator;

  @Mock
  private OpenWeatherClient openWeatherClient;

  @InjectMocks
  private WeatherServiceImpl weatherService;

  @Test
  @DisplayName("위도와 경도로 날씨 목록을 조회하면 24시간 예보가 포함된 List<WeatherDto>를 반환한다")
  void getWeathers_success() {
    // given
    Double latitude = 37.4979;
    Double longitude = 127.0276;
    WeatherAPILocation mockLocation = new WeatherAPILocation(
        latitude, longitude, 61, 125, List.of("서울특별시", "강남구")
    );
    Weather mockWeather = WeatherFixture.createCustom(18.5, SkyStatus.CLEAR, PrecipitationType.NONE);
    ReflectionTestUtils.setField(mockWeather, "id", UUID.randomUUID());

    given(locationService.getLocation(latitude, longitude)).willReturn(mockLocation);
    given(weatherRepository.findAllByLatitudeAndLongitudeAndForecastAtBetweenOrderByForecastAtAsc(
        eq(latitude), eq(longitude), any(), any()
    )).willReturn(List.of(mockWeather));

    // when
    List<WeatherDto> result = weatherService.getWeathers(longitude, latitude);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isNotNull();
    assertThat(result.get(0).temperature().current()).isEqualTo(18.5);
    assertThat(result.get(0).skyStatus()).isEqualTo(SkyStatus.CLEAR);
    assertThat(result.get(0).location().locationNames()).containsExactly("서울특별시", "강남구");
  }

  @Test
  @DisplayName("DB에도 없고 외부 실시간 수집도 실패하면 WeatherNotFoundException이 발생한다")
  void getWeathers_whenBothDbAndExternalFail_throwsNotFound() {
    // given
    Double latitude = 37.4979;
    Double longitude = 127.0276;
    WeatherAPILocation mockLocation = new WeatherAPILocation(
        latitude, longitude, 61, 125, List.of("서울특별시", "강남구")
    );

    given(locationService.getLocation(latitude, longitude)).willReturn(mockLocation);
    given(weatherRepository.findAllByLatitudeAndLongitudeAndForecastAtBetweenOrderByForecastAtAsc(
        eq(latitude), eq(longitude), any(), any()
    )).willReturn(Collections.emptyList());
    given(openWeatherClient.fetchForecast(latitude, longitude)).willReturn(Collections.emptyList());

    // when & then
    assertThatThrownBy(() -> weatherService.getWeathers(longitude, latitude))
        .isInstanceOf(WeatherNotFoundException.class)
        .hasMessage("날씨 정보가 존재하지 않습니다.");
  }

  @Test
  @DisplayName("날씨 목록 조회 시 최저/최고 기온 및 전날 대비 기온 차이가 계산되어 포함된다")
  void getWeathers_withTemperatureCalculation_success() {
    // given
    Double latitude = 37.4979;
    Double longitude = 127.0276;
    WeatherAPILocation mockLocation = new WeatherAPILocation(
        latitude, longitude, 61, 125, List.of("서울특별시", "강남구")
    );
    Weather mockWeather = WeatherFixture.createCustom(22.0, SkyStatus.CLEAR, PrecipitationType.NONE);
    ReflectionTestUtils.setField(mockWeather, "id", UUID.randomUUID());
    given(locationService.getLocation(latitude, longitude)).willReturn(mockLocation);
    given(weatherRepository.findAllByLatitudeAndLongitudeAndForecastAtBetweenOrderByForecastAtAsc(
        eq(latitude), eq(longitude), any(), any()
    )).willReturn(List.of(mockWeather));
    given(weatherRepository.findTopByLatitudeAndLongitudeAndForecastAtLessThanEqualOrderByForecastAtDesc(
        eq(latitude), eq(longitude), any()
    )).willReturn(Optional.empty()); // 어제 데이터 없음 가정
    given(temperatureCalculator.calculateMin(any())).willReturn(18.0);
    given(temperatureCalculator.calculateMax(any())).willReturn(24.5);
    given(temperatureCalculator.calculateDifference(any(), any())).willReturn(2.5);
    // when
    List<WeatherDto> result = weatherService.getWeathers(longitude, latitude);
    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).temperature().current()).isEqualTo(22.0);
    assertThat(result.get(0).temperature().min()).isEqualTo(18.0);
    assertThat(result.get(0).temperature().max()).isEqualTo(24.5);
    assertThat(result.get(0).temperature().comparedToDayBefore()).isEqualTo(2.5);
  }

  @Test
  @DisplayName("조회된 24시간 예보에 오늘과 내일 데이터가 섞여있을 경우 날짜별로 최저/최고 기온이 각각 분리 집계된다")
  void getWeathers_calculatesMinMaxSeparatelyByDate() {
    // given
    Double latitude = 37.4979;
    Double longitude = 127.0276;
    WeatherAPILocation mockLocation = new WeatherAPILocation(latitude, longitude, 61, 125, List.of("서울"));
    ZoneId KST = ZoneId.of("Asia/Seoul");
    // 자바가 한국 시간 11월 17일과 11월 18일로 알아서 정확하게 시차 계산
    Instant today = LocalDate.of(2026, 11, 17).atStartOfDay(KST).toInstant();
    Instant tomorrow = LocalDate.of(2026, 11, 18).atStartOfDay(KST).toInstant();
    Weather todayWeather = WeatherFixture.createCustom(22.0, SkyStatus.CLEAR, PrecipitationType.NONE);
    ReflectionTestUtils.setField(todayWeather, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(todayWeather, "forecastAt", today);
    Weather tomorrowWeather = WeatherFixture.createCustom(10.0, SkyStatus.CLEAR, PrecipitationType.NONE);
    ReflectionTestUtils.setField(tomorrowWeather, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(tomorrowWeather, "forecastAt", tomorrow);
    given(locationService.getLocation(latitude, longitude)).willReturn(mockLocation);
    given(weatherRepository.findAllByLatitudeAndLongitudeAndForecastAtBetweenOrderByForecastAtAsc(any(), any(), any(), any()))
        .willReturn(List.of(todayWeather, tomorrowWeather));
    given(weatherRepository.findTopByLatitudeAndLongitudeAndForecastAtLessThanEqualOrderByForecastAtDesc(any(), any(), any()))
        .willReturn(Optional.empty());
    // 오늘 날짜 기온(22.0) vs 내일 날짜 기온(10.0) 분리
    given(temperatureCalculator.calculateMin(List.of(22.0))).willReturn(18.0);
    given(temperatureCalculator.calculateMax(List.of(22.0))).willReturn(24.0);
    given(temperatureCalculator.calculateMin(List.of(10.0))).willReturn(8.0);
    given(temperatureCalculator.calculateMax(List.of(10.0))).willReturn(12.0);

    // when
    List<WeatherDto> result = weatherService.getWeathers(longitude, latitude);

    // then
    assertThat(result).hasSize(2);
    assertThat(result.get(0).temperature().min()).isEqualTo(18.0);
    assertThat(result.get(0).temperature().max()).isEqualTo(24.0);
    assertThat(result.get(1).temperature().min()).isEqualTo(8.0);
    assertThat(result.get(1).temperature().max()).isEqualTo(12.0);
  }



}