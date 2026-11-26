package com.otboo.team03.weather.batch;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class WeatherBatchConfig {

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;
  private final WeatherCollectionTasklet weatherCollectionTasklet;

  @Bean
  public Job weatherCollectionJob() {
    return new JobBuilder("weatherCollectionJob", jobRepository)
        .start(weatherCollectionStep())
        .build();
  }

  @Bean
  public Step weatherCollectionStep() {
    return new StepBuilder("weatherCollectionStep", jobRepository)
        .tasklet(weatherCollectionTasklet, transactionManager)
        .build();
  }

}