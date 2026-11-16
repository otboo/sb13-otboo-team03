package com.otboo.team03.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.otboo.team03.profile.dto.WeatherAPILocation;
import com.otboo.team03.weather.dto.WeatherDto;
import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.exception.WeatherNotFoundException;
import com.otboo.team03.weather.fixture.WeatherFixture;
import com.otboo.team03.weather.repository.WeatherRepository;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

  @Mock
  private LocationService locationService;

  @Mock
  private WeatherRepository weatherRepository;

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
  @DisplayName("해당 좌표에 조회 가능한 예보 데이터가 없으면 WeatherNotFoundException이 발생한다")
  void getWeathers_notFound() {
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

    // when & then
    assertThatThrownBy(() -> weatherService.getWeathers(longitude, latitude))
        .isInstanceOf(WeatherNotFoundException.class)
        .hasMessage("해당 지역의 날씨 정보가 존재하지 않습니다.");
  }
}