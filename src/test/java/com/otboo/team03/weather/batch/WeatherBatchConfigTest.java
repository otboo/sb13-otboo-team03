package com.otboo.team03.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class WeatherBatchConfigTest {

  @Mock
  private JobRepository jobRepository;

  @Mock
  private PlatformTransactionManager transactionManager;

  @Mock
  private WeatherCollectionTasklet tasklet;

  @Test
  @DisplayName("WeatherBatchConfig가 Job과 Step 빈을 정상적으로 생성한다")
  void batchConfig_createsJobAndStep() {
    WeatherBatchConfig config = new WeatherBatchConfig(jobRepository, transactionManager, tasklet);

    Step step = config.weatherCollectionStep();
    assertThat(step).isNotNull();
    assertThat(step.getName()).isEqualTo("weatherCollectionStep");

    Job job = config.weatherCollectionJob();
    assertThat(job).isNotNull();
    assertThat(job.getName()).isEqualTo("weatherCollectionJob");
  }


}