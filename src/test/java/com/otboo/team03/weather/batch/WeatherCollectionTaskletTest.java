package com.otboo.team03.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import com.otboo.team03.weather.client.OpenWeatherClient;
import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.repository.WeatherRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

@ExtendWith(MockitoExtension.class)
class WeatherCollectionTaskletTest {

  @Mock
  private OpenWeatherClient openWeatherClient;

  @Mock
  private WeatherRepository weatherRepository;

  @InjectMocks
  private WeatherCollectionTasklet tasklet;

  private Weather createWeather(Double lat, Double lon, Instant forecastAt) {
    return Weather.builder()
        .latitude(lat)
        .longitude(lon)
        .forecastedAt(Instant.now())
        .forecastAt(forecastAt)
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(PrecipitationType.NONE)
        .precipitationAmount(0.0)
        .precipitationProbability(0.0)
        .temperature(20.0)
        .humidity(50.0)
        .windSpeed(2.0)
        .build();
  }

  @Test
  @DisplayName("외부 API로부터 날씨 데이터를 정상 수집하여 중복되지 않은 신규 데이터만 저장한다")
  void execute_savesNewWeathersSuccessfully() {
    // given
    Instant time1 = Instant.parse("2026-11-19T03:00:00Z");
    Instant time2 = Instant.parse("2026-11-19T06:00:00Z");
    Weather weather1 = createWeather(37.4979, 127.0276, time1);
    Weather weather2 = createWeather(37.4979, 127.0276, time2);

    given(openWeatherClient.fetchForecast(anyDouble(), anyDouble()))
        .willReturn(List.of(weather1, weather2));

    // time1은 이미 DB에 존재, time2는 신규 데이터라고 가정
    given(weatherRepository.existsByLatitudeAndLongitudeAndForecastAt(anyDouble(), anyDouble(), eq(time1)))
        .willReturn(true);
    given(weatherRepository.existsByLatitudeAndLongitudeAndForecastAt(anyDouble(), anyDouble(), eq(time2)))
        .willReturn(false);

    // when
    RepeatStatus status = tasklet.execute(null, null);

    // then
    assertThat(status).isEqualTo(RepeatStatus.FINISHED);
    // 중복된 time1은 제외되고 신규 데이터인 time2만 저장되었는지 검증
    verify(weatherRepository, times(2)).saveAll(List.of(weather2));
  }

  @Test
  @DisplayName("수집 대상 지역 중 특정 지역 호출에서 예외가 발생해도 배치가 중단되지 않고 정상 완료된다")
  void execute_whenApiExceptionOccurs_continuesNextTarget() {
    // given (API 호출 시 네트워크 예외 발생 시뮬레이션)
    given(openWeatherClient.fetchForecast(anyDouble(), anyDouble()))
        .willThrow(new RuntimeException("API Connection Timeout"));

    // when
    RepeatStatus status = tasklet.execute(null, null);

    // then: 예외가 상위로 터지지 않고 장애 격리되어 FINISHED로 안전하게 끝남
    assertThat(status).isEqualTo(RepeatStatus.FINISHED);
    verify(weatherRepository, never()).saveAll(any());
  }

  @Test
  @DisplayName("모든 예보 데이터가 이미 DB에 존재하는 경우 신규 저장을 수행하지 않는다")
  void execute_whenAllDataAlreadyExists_doesNotSave() {
    // given
    Instant time1 = Instant.parse("2026-11-19T03:00:00Z");
    Weather weather1 = createWeather(37.4979, 127.0276, time1);

    given(openWeatherClient.fetchForecast(anyDouble(), anyDouble()))
        .willReturn(List.of(weather1));
    // 이미 존재하는 데이터로 설정
    given(weatherRepository.existsByLatitudeAndLongitudeAndForecastAt(anyDouble(), anyDouble(), eq(time1)))
        .willReturn(true);

    // when
    RepeatStatus status = tasklet.execute(null, null);

    // then
    assertThat(status).isEqualTo(RepeatStatus.FINISHED);
    verify(weatherRepository, never()).saveAll(any());
  }


}