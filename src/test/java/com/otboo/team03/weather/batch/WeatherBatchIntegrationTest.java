package com.otboo.team03.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import com.otboo.team03.weather.client.OpenWeatherClient;
import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.repository.WeatherRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class WeatherBatchIntegrationTest {

  @Autowired
  private JobOperator jobOperator;

  @Autowired
  private Job weatherCollectionJob;

  @Autowired
  private WeatherRepository weatherRepository;

  // 실제 외부 API 호출을 방지하고 테스트 데이터를 공급하기 위해 모킹
  @MockitoBean
  private OpenWeatherClient openWeatherClient;

  @BeforeEach
  void setUp() {
    weatherRepository.deleteAll();
  }

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
  @DisplayName("거점별 예보 수집 시 신규 적재와 기존 예보 갱신이 실제 DB에 각각 정상 반영된다")
  void weatherCollectionJob_executesSuccessfullyWithInsertAndUpdate() throws Exception {
    // given
    Instant forecastTime = Instant.parse("2026-11-20T12:00:00Z");
    Weather existingGangnam = weatherRepository.save(
        createWeather(37.4979, 127.0276, forecastTime, 18.0)
    );
    Weather newGangnam = createWeather(37.4979, 127.0276, forecastTime, 15.0);
    Weather newJung = createWeather(37.5665, 126.9780, forecastTime, 21.0);

    given(openWeatherClient.fetchForecast(eq(37.4979), anyDouble()))
        .willReturn(List.of(newGangnam));
    given(openWeatherClient.fetchForecast(eq(37.5665), anyDouble()))
        .willReturn(List.of(newJung));

    JobParameters jobParameters = new JobParametersBuilder()
        .addLong("timestamp", System.currentTimeMillis())
        .toJobParameters();

    // when
    jobOperator.start(weatherCollectionJob, jobParameters);

    // then
    Optional<Weather> updatedGangnam = weatherRepository.findById(existingGangnam.getId());
    assertThat(updatedGangnam).isPresent();
    assertThat(updatedGangnam.get().getTemperature()).isEqualTo(15.0);

    List<Weather> jungguList = weatherRepository.findAllByLatitudeAndLongitudeAndForecastAtBetweenOrderByForecastAtAsc(
        37.5665, 126.9780, forecastTime.minusSeconds(1), forecastTime.plusSeconds(1)
    );
    assertThat(jungguList).hasSize(1);
    assertThat(jungguList.get(0).getTemperature()).isEqualTo(21.0);
  }


}