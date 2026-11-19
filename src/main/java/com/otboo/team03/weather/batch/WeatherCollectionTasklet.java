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

        List<Weather> newWeathers = weathers.stream()
            .filter(w -> !weatherRepository.existsByLatitudeAndLongitudeAndForecastAt(
                w.getLatitude(), w.getLongitude(), w.getForecastAt()))
            .toList();

        if (!newWeathers.isEmpty()) {
          weatherRepository.saveAll(newWeathers);
          log.info(">>>>> [WeatherBatch] {} 신규 날씨 데이터 {}건 저장 완료", target.name(), newWeathers.size());
        } else {
          log.info(">>>>> [WeatherBatch] {} 모든 예보 데이터가 이미 최신 상태입니다.", target.name());
        }
      } catch (Exception e) {
        log.error(">>>>> [WeatherBatch] {} 날씨 수집 중 오류 발생: {}", target.name(), e.getMessage());
      }
    }

    return RepeatStatus.FINISHED;
  }

}