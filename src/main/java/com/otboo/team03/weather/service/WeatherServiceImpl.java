package com.otboo.team03.weather.service;

import com.otboo.team03.profile.dto.WeatherAPILocation;
import com.otboo.team03.weather.dto.WeatherDto;
import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.exception.WeatherNotFoundException;
import com.otboo.team03.weather.repository.WeatherRepository;
import com.otboo.team03.weather.util.TemperatureCalculator;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeatherServiceImpl implements WeatherService {

  private final LocationService locationService;
  private final WeatherRepository weatherRepository;
  private final TemperatureCalculator temperatureCalculator;

  @Override
  public List<WeatherDto> getWeathers(Double longitude, Double latitude) {
    WeatherAPILocation location = locationService.getLocation(latitude, longitude);

    Instant now = Instant.now();
    Instant end = now.plus(Duration.ofHours(24));

    List<Weather> weathers = weatherRepository
        .findAllByLatitudeAndLongitudeAndForecastAtBetweenOrderByForecastAtAsc(latitude, longitude, now, end);

    if (weathers.isEmpty()) {
      throw new WeatherNotFoundException();
    }

    ZoneId KST = ZoneId.of("Asia/Seoul");
    // 1. 예보 데이터를 대상 일자(LocalDate)별 기온 목록으로 그룹화
    Map<LocalDate, List<Double>> tempsByDate = weathers.stream()
        .collect(Collectors.groupingBy(
            w -> w.getForecastAt().atZone(KST).toLocalDate(),
            Collectors.mapping(Weather::getTemperature, Collectors.toList())
        ));
    // 2. 날짜별 일 최저/최고 기온 각각 산출
    Map<LocalDate, Double> minTempByDate = new HashMap<>();
    Map<LocalDate, Double> maxTempByDate = new HashMap<>();
    tempsByDate.forEach((date, temps) -> {
      minTempByDate.put(date, temperatureCalculator.calculateMin(temps));
      maxTempByDate.put(date, temperatureCalculator.calculateMax(temps));
    });
    // 3. 어제 기온 조회 및 어제 대비 차이 계산 (어제 동시간대 기준 24시간 전)
    Instant yesterdayTime = now.minus(Duration.ofHours(24));
    Optional<Weather> yesterdayWeather = weatherRepository
        .findTopByLatitudeAndLongitudeAndForecastAtLessThanEqualOrderByForecastAtDesc(latitude, longitude, yesterdayTime);
    Double currentTemp = weathers.get(0).getTemperature();
    Double yesterdayTemp = yesterdayWeather.map(Weather::getTemperature).orElse(null);
    Double tempDiff = temperatureCalculator.calculateDifference(currentTemp, yesterdayTemp);
    // 4. 각 예보의 날짜에 맞는 최저/최고 기온을 매핑하여 DTO 생성
    return weathers.stream()
        .map(weather -> {
          LocalDate forecastDate = weather.getForecastAt().atZone(KST).toLocalDate();
          Double minTemp = minTempByDate.get(forecastDate);
          Double maxTemp = maxTempByDate.get(forecastDate);
          return WeatherDto.from(weather, location, minTemp, maxTemp, tempDiff);
        })
        .toList();
  }
}