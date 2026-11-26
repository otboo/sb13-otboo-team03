package com.otboo.team03.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class WeatherBatchSchedulerTest {

  @Mock
  private JobOperator jobOperator;

  @Mock
  private Job weatherCollectionJob;

  @InjectMocks
  private WeatherBatchScheduler weatherBatchScheduler;

  @Test
  @DisplayName("스케줄러 메서드에 @Scheduled 매시간 정각 cron 설정이 선언되어 있다")
  void runWeatherCollectionJob_hasScheduledAnnotationWithHourlyCron() throws Exception {
    Method method = WeatherBatchScheduler.class.getMethod("runWeatherCollectionJob");
    Scheduled scheduled = method.getAnnotation(Scheduled.class);

    assertThat(scheduled).isNotNull();
    assertThat(scheduled.cron()).isEqualTo("0 0 * * * *");
  }

  @Test
  @DisplayName("스케줄러 실행 시 JobOperator를 통해 runAt 파라미터가 담긴 weatherCollectionJob을 정상 실행한다")
  void runWeatherCollectionJob_successfullyStartsJobWithRunAtParameter() throws Exception {
    // when
    weatherBatchScheduler.runWeatherCollectionJob();

    // then: ArgumentCaptor로 runAt 파라미터가 실제 전달되었는지 검증
    ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
    verify(jobOperator).start(eq(weatherCollectionJob), captor.capture());

    JobParameters capturedParams = captor.getValue();
    assertThat(capturedParams.getLocalDateTime("runAt")).isNotNull();
    // 분과 초가 00:00(정각)으로 잘 고정되었는지 검증
    LocalDateTime runAt = capturedParams.getLocalDateTime("runAt");
    assertThat(runAt.getMinute()).isZero();
    assertThat(runAt.getSecond()).isZero();
  }

  @Test
  @DisplayName("Job 시작 요청 실패 시 예외가 스케줄러 메서드 밖으로 전파되지 않는다")
  void runWeatherCollectionJob_whenExceptionOccurs_handlesGracefully() throws Exception {
    // given
    ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
    doThrow(new RuntimeException("JobExecutionException"))
        .when(jobOperator).start(eq(weatherCollectionJob), captor.capture());

    // when & then: 예외가 상위로 터지지 않고 안전하게 try-catch 됨을 검증
    assertDoesNotThrow(() -> weatherBatchScheduler.runWeatherCollectionJob());
  }

}
