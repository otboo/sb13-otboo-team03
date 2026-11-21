package com.otboo.team03.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepContribution;
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
  @DisplayName("신규 예보 데이터인 경우 일괄 조회 후 신규 데이터만 saveAll로 저장한다")
  void execute_savesNewWeathersSuccessfully() {
    // given
    Instant time1 = Instant.parse("2026-11-19T03:00:00Z");
    Weather fetched = createWeather(37.4979, 127.0276, time1, 20.0);

    given(openWeatherClient.fetchForecast(anyDouble(), anyDouble()))
        .willReturn(List.of(fetched));

    given(weatherRepository.findAllByLatitudeAndLongitudeAndForecastAtIn(anyDouble(), anyDouble(), any()))
        .willReturn(Collections.emptyList());

    // when
    RepeatStatus status = tasklet.execute(null, null);

    // then
    assertThat(status).isEqualTo(RepeatStatus.FINISHED);
    verify(weatherRepository, times(2)).saveAll(List.of(fetched));
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
    given(weatherRepository.findAllByLatitudeAndLongitudeAndForecastAtIn(anyDouble(), anyDouble(), any()))
        .willReturn(List.of(existing));

    // when
    RepeatStatus status = tasklet.execute(null, null);

    // then
    assertThat(status).isEqualTo(RepeatStatus.FINISHED);
    // 기존 데이터의 기온이 최신 예보인 15.0도로 업데이트되었는지 확인
    assertThat(existing.getTemperature()).isEqualTo(15.0);
    verify(weatherRepository, never()).saveAll(any());
  }

  @Test
  @DisplayName("첫 번째 거점(강남) 호출이 실패해도 두 번째 거점(중구)은 정상적으로 수집되어 저장된다 (장애 격리)")
  void execute_whenFirstTargetFails_secondTargetSucceeds() {
    // given
    Weather jungguWeather = createWeather(37.5665, 126.9780, Instant.parse("2026-11-19T03:00:00Z"), 19.0);

    // 강남(37.4979)은 API 예외 발생, 중구(37.5665)는 정상 데이터 반환
    given(openWeatherClient.fetchForecast(eq(37.4979), anyDouble()))
        .willThrow(new RuntimeException("Gangnam API Timeout"));
    given(openWeatherClient.fetchForecast(eq(37.5665), anyDouble()))
        .willReturn(List.of(jungguWeather));

    given(weatherRepository.findAllByLatitudeAndLongitudeAndForecastAtIn(eq(37.5665), anyDouble(), any()))
        .willReturn(Collections.emptyList());

    // when
    RepeatStatus status = tasklet.execute(null, null);

    // then: 전체 배치는 정상 종료되고, 중구 데이터는 성공적으로 저장됨
    assertThat(status).isEqualTo(RepeatStatus.FINISHED);
    verify(weatherRepository, times(1)).saveAll(List.of(jungguWeather));
  }

  @Test
  @DisplayName("모든 대상 지역 수집이 실패한 경우 Step이 FAILED 처리되고 예외를 던진다")
  void execute_whenAllTargetsFail_stepBecomesFailed() {
    // given: 강남, 중구 모두 실패
    given(openWeatherClient.fetchForecast(anyDouble(), anyDouble()))
        .willReturn(Collections.emptyList());

    StepContribution contribution = org.mockito.Mockito.mock(StepContribution.class);

    // when & then
    assertThatThrownBy(() -> tasklet.execute(contribution, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("모든 대상 지역의 날씨 수집에 실패했습니다.");

    verify(contribution).setExitStatus(ExitStatus.FAILED);
  }


}
