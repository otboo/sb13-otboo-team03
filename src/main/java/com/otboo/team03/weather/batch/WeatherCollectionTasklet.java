package com.otboo.team03.weather.batch;

import com.otboo.team03.weather.client.OpenWeatherClient;
import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.repository.WeatherRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
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
      new TargetLocation("서울 중구", 37.5665, 126.9780),
      new TargetLocation("경기 수원시 팔달구", 37.2636, 127.0286),
      new TargetLocation("경기 성남시 분당구", 37.3827, 127.1189),
      new TargetLocation("부산 해운대구", 35.1631, 129.1636)
  );

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    log.info(">>>>> [WeatherBatch] OpenWeatherMap 날씨 데이터 자동 수집 시작");

    int successCount = 0;
    int failCount = 0;

    for (TargetLocation target : TARGETS) {
      try {
        List<Weather> weathers = openWeatherClient.fetchForecast(target.lat(), target.lon());
        if (weathers == null || weathers.isEmpty()) {
          log.warn(">>>>> [WeatherBatch] {} 날씨 데이터를 수집하지 못했습니다.", target.name());
          failCount++;
          continue;
        }

        // 1. 수집된 예보 시각 목록 추출
        List<Instant> forecastAts = weathers.stream()
            .map(Weather::getForecastAt)
            .toList();

        // 2. DB에서 기존 예보들을 한 번에 일괄 조회하여 Map으로 매핑 (N+1 SELECT 방지)
        Map<Instant, Weather> existingMap = weatherRepository
            .findAllByLatitudeAndLongitudeAndForecastAtIn(target.lat(), target.lon(), forecastAts)
            .stream()
            .collect(Collectors.toMap(Weather::getForecastAt, w -> w));

        int newCount = 0;
        int updateCount = 0;
        List<Weather> newWeathers = new ArrayList<>();

        for (Weather fetched : weathers) {
          Weather existing = existingMap.get(fetched.getForecastAt());
          if (existing != null) {
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
            updateCount++;
          } else {
            newWeathers.add(fetched);
            newCount++;
          }
        }

        if (!newWeathers.isEmpty()) {
          weatherRepository.saveAll(newWeathers);
        }

        log.info(">>>>> [WeatherBatch] {} 날씨 수집 완료 - 신규: {}건, 최신 갱신: {}건", target.name(), newCount, updateCount);
        successCount++;
      } catch (Exception e) {
        log.error(">>>>> [WeatherBatch] {} 날씨 수집 중 오류 발생: {}", target.name(), e.getMessage());
        failCount++;
      }
    }

    // 모든 대상 지역 수집이 실패한 경우 Step을 FAILED로 처리하여 배치 실패 인지
    if (failCount > 0 && successCount == 0) {
      log.error(">>>>> [WeatherBatch] 모든 대상 지역의 날씨 수집에 실패하여 Step을 FAILED 처리합니다.");
      contribution.setExitStatus(ExitStatus.FAILED);
      throw new IllegalStateException("모든 대상 지역의 날씨 수집에 실패했습니다.");
    }

    return RepeatStatus.FINISHED;
  }

}