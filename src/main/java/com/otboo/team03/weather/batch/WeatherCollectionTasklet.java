package com.otboo.team03.weather.batch;

import com.otboo.team03.weather.client.OpenWeatherClient;
import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.repository.WeatherRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherCollectionTasklet implements Tasklet {

  private final OpenWeatherClient openWeatherClient;
  private final WeatherRepository weatherRepository;

  // 서비스 주요 거점 좌표 목록 (예: 서울 강남, 종로 등)
  private record TargetLocation(String name, double lat, double lon) {}
  private static final List<TargetLocation> TARGETS = List.of(
      new TargetLocation("서울 강남구", 37.4979, 127.0276),
      new TargetLocation("서울 중구", 37.5665, 126.9780)
  );

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    log.info(">>>>> [WeatherBatch] OpenWeatherMap 날씨 데이터 자동 수집 시작");

    for (TargetLocation target : TARGETS) {
      try {
        List<Weather> weathers = openWeatherClient.fetchForecast(target.lat(), target.lon());

        int newCount = 0;
        int updateCount = 0;

        for (Weather fetched : weathers) {
          var existingOpt = weatherRepository.findByLatitudeAndLongitudeAndForecastAt(
              fetched.getLatitude(), fetched.getLongitude(), fetched.getForecastAt());
          if (existingOpt.isPresent()) {
            // 이미 존재하는 예보 시각: 최신 예보 수치로 업데이트
            Weather existing = existingOpt.get();
            existing.updateForecast(
                fetched.getForecastedAt(),
                fetched.getSkyStatus(),
                fetched.getPrecipitationType(),
                fetched.getPrecipitationAmount(),
                fetched.getPrecipitationProbability(),
                fetched.getTemperature(),
                fetched.getHumidity(),
                fetched.getWindSpeed()
            );
            weatherRepository.save(existing);
            updateCount++;
          } else {
            // 신규 예보 시각: 새로 저장
            weatherRepository.save(fetched);
            newCount++;
          }
        }
        log.info(">>>>> [WeatherBatch] {} 날씨 수집 완료 - 신규: {}건, 최신 갱신: {}건", target.name(), newCount, updateCount);
      } catch (Exception e) {
        log.error(">>>>> [WeatherBatch] {} 날씨 수집 중 오류 발생: {}", target.name(), e.getMessage());
      }
    }

    return RepeatStatus.FINISHED;
  }

}