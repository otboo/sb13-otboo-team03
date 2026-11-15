package com.otboo.team03.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import com.otboo.team03.weather.dto.WeatherResponse;
import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.exception.WeatherNotFoundException;
import com.otboo.team03.weather.fixture.WeatherFixture;
import com.otboo.team03.weather.repository.WeatherRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

  @Mock
  private WeatherRepository weatherRepository;

  @InjectMocks
  private WeatherServiceImpl weatherService;

  @Test
  @DisplayName("좌표로 날씨 조회 시 현재 날씨와 시간대별 예보 목록을 정상 반환한다")
  void getWeather_success() {
    // given: 테스트 데이터 및 Mock 가짜 응답 설정
    Double lat = 37.4979;
    Double lon = 127.0276;
    Weather currentWeather = WeatherFixture.createDefault();
    List<Weather> forecastList = List.of(currentWeather);

    given(weatherRepository.findTopByLatitudeAndLongitudeAndForecastAtLessThanEqualOrderByForecastAtDesc(eq(lat), eq(lon), any()))
        .willReturn(Optional.of(currentWeather));

    given(weatherRepository.findAllByLatitudeAndLongitudeAndForecastAtBetweenOrderByForecastAtAsc(eq(lat), eq(lon), any(), any()))
       .willReturn(forecastList);

    // when: 실제 서비스 메서드 호출
    WeatherResponse response = weatherService.getWeather(lat, lon);

    // then: 결과 검증
    assertThat(response).isNotNull();
    assertThat(response.currentWeather().temperature()).isEqualTo(currentWeather.getTemperature());
    assertThat(response.hourlyForecasts()).hasSize(1);
  }

  @Test
  @DisplayName("해당 좌표의 날씨 데이터가 없으면 WeatherNotFoundException이 발생한다")
  void getWeather_notFound() {
    // [given]
    Double lat = 37.4979;
    Double lon = 127.0276;

    given(weatherRepository.findTopByLatitudeAndLongitudeAndForecastAtLessThanEqualOrderByForecastAtDesc(eq(lat), eq(lon), any()))
        .willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> weatherService.getWeather(lat, lon))
        .isInstanceOf(WeatherNotFoundException.class)
        .hasMessage("날씨 정보가 존재하지 않습니다.");
  }


}