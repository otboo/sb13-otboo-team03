package com.otboo.team03.weather.batch;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherBatchScheduler {

  private final JobOperator jobOperator;
  private final Job weatherCollectionJob;

  // 매시간 정각(0분 0초)마다 자동으로 날씨 수집 배치 실행
  // cron = "초 분 시 일 월 요일"
  @Scheduled(cron = "0 0 * * * *")
  public void runWeatherCollectionJob() {
    try {
      JobParameters jobParameters = new JobParametersBuilder()
          .addLocalDateTime("runAt", LocalDateTime.now())
          .toJobParameters();

      log.info(">>>>> [WeatherBatchScheduler] 날씨 데이터 자동 수집 배치 실행 시작: {}", LocalDateTime.now());
      jobOperator.start(weatherCollectionJob, jobParameters);
    } catch (Exception e) {
      log.error(">>>>> [WeatherBatchScheduler] 날씨 데이터 자동 수집 배치 실행 중 오류 발생: {}", e.getMessage());
    }
  }

}