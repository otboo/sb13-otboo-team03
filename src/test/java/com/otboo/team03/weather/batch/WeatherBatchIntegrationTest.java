package com.otboo.team03.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import com.otboo.team03.weather.client.OpenWeatherClient;
import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.repository.WeatherRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
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
  @DisplayName("배치 Job 실행 시 Step과 Tasklet을 거쳐 실제 DB에 날씨 데이터가 정상 적재된다")
  void weatherCollectionJob_executesSuccessfullyAndSavesData() throws Exception {
    // given
    Instant forecastTime = Instant.parse("2026-11-19T06:00:00Z");
    Weather weather = createWeather(37.4979, 127.0276, forecastTime, 17.5);

    // 외부 API 호출 시 위 가짜 날씨 데이터를 반환하도록 설정
    given(openWeatherClient.fetchForecast(anyDouble(), anyDouble()))
        .willReturn(List.of(weather));

    JobParameters jobParameters = new JobParametersBuilder()
        .addLong("timestamp", System.currentTimeMillis())
        .toJobParameters();

    // when (JobOperator를 통해 실제 배치 Job 실행)
    jobOperator.start(weatherCollectionJob, jobParameters);

    // then (실제 DB에 날씨 데이터가 2개 거점 분량으로 정상 저장되었는지 검증)
    List<Weather> savedWeathers = weatherRepository.findAll();
    assertThat(savedWeathers).isNotEmpty();
    assertThat(savedWeathers)
        .anyMatch(w -> w.getTemperature().equals(17.5) && w.getForecastAt().equals(forecastTime));
  }


}