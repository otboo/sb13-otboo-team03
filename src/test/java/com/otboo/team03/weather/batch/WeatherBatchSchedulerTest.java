package com.otboo.team03.weather.batch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;

@ExtendWith(MockitoExtension.class)
class WeatherBatchSchedulerTest {

  @Mock
  private JobOperator jobOperator;

  @Mock
  private Job weatherCollectionJob;

  @InjectMocks
  private WeatherBatchScheduler weatherBatchScheduler;

  @Test
  @DisplayName("스케줄러 실행 시 JobOperator를 통해 weatherCollectionJob을 정상 실행한다")
  void runWeatherCollectionJob_successfullyStartsJob() throws Exception {
    // when
    weatherBatchScheduler.runWeatherCollectionJob();

    // then
    verify(jobOperator).start(eq(weatherCollectionJob), any(JobParameters.class));
  }

  @Test
  @DisplayName("Job 실행 중 예외가 발생해도 예외를 처리하고 애플케이션이 중단되지 않는다")
  void runWeatherCollectionJob_whenExceptionOccurs_handlesGracefully() throws Exception {
    // given
    doThrow(new RuntimeException("JobExecutionException"))
        .when(jobOperator).start(eq(weatherCollectionJob), any(JobParameters.class));

    // when
    weatherBatchScheduler.runWeatherCollectionJob();

    // then
    verify(jobOperator).start(eq(weatherCollectionJob), any(JobParameters.class));
  }

}
