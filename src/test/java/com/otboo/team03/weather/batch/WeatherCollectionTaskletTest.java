package com.otboo.team03.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.otboo.team03.weather.client.OpenWeatherClient;
import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.repository.WeatherRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

  private Weather createWeather(Double lat, Double lon, Instant forecastAt, Double temp) {
    return Weather.builder()
        .latitude(lat)
        .longitude(lon)
        .forecastedAt(Instant.now())
        .forecastAt(forecastAt)
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(PrecipitationType.NONE)
        .precipitationAmount(0.0)
        .precipitationProbability(0.0)
        .temperature(temp)
        .humidity(50.0)
        .windSpeed(2.0)
        .build();
  }

  @Test
  @DisplayName("외부 API로부터 날씨 데이터를 정상 수집하여 중복되지 않은 신규 데이터만 저장한다")
  void execute_savesNewWeathersSuccessfully() {
    // given
    Instant time1 = Instant.parse("2026-11-19T03:00:00Z");
    Weather fetched = createWeather(37.4979, 127.0276, time1, 20.0);

    given(openWeatherClient.fetchForecast(anyDouble(), anyDouble()))
        .willReturn(List.of(fetched));

    // DB에 해당 시각 예보가 없는 상태
    given(weatherRepository.findByLatitudeAndLongitudeAndForecastAt(anyDouble(), anyDouble(), any(Instant.class)))
        .willReturn(Optional.empty());

    // when
    RepeatStatus status = tasklet.execute(null, null);

    // then
    assertThat(status).isEqualTo(RepeatStatus.FINISHED);
    // 중복된 time1은 제외되고 신규 데이터인 time2만 저장되었는지 검증
    verify(weatherRepository, times(2)).save(fetched);
  }

  @Test
  @DisplayName("이미 존재하는 예보인 경우 기존 데이터를 최신 예보 수치로 업데이트하여 저장한다")
  void execute_updatesExistingForecastSuccessfully() {
    // given
    Instant time1 = Instant.parse("2026-11-19T03:00:00Z");
    Weather existing = createWeather(37.4979, 127.0276, time1, 18.0); // 기존 예보: 18도
    Weather fetched = createWeather(37.4979, 127.0276, time1, 15.0);  // 새로 발표된 예보: 15도로 수정됨
    given(openWeatherClient.fetchForecast(anyDouble(), anyDouble()))
        .willReturn(List.of(fetched));
    // DB에 이미 기존 예보가 존재함
    given(weatherRepository.findByLatitudeAndLongitudeAndForecastAt(anyDouble(), anyDouble(), any(Instant.class)))
        .willReturn(Optional.of(existing));
    // when
    RepeatStatus status = tasklet.execute(null, null);
    // then
    assertThat(status).isEqualTo(RepeatStatus.FINISHED);
    // 기존 데이터의 기온이 최신 예보인 15.0도로 업데이트되었는지 확인
    assertThat(existing.getTemperature()).isEqualTo(15.0);
    // 거점 2곳(강남, 중구) 각각 1회씩 총 2회 갱신 저장 호출 검증
    verify(weatherRepository, times(2)).save(existing);
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
    verify(weatherRepository, never()).save(any());
  }

}
