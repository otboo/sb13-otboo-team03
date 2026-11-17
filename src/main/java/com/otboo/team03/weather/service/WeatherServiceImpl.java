package com.otboo.team03.weather.service;

import com.otboo.team03.profile.dto.WeatherAPILocation;
import com.otboo.team03.weather.dto.WeatherDto;
import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.exception.WeatherNotFoundException;
import com.otboo.team03.weather.repository.WeatherRepository;
import com.otboo.team03.weather.util.TemperatureCalculator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

    // 기온 목록 추출 및 일 최저/최고 기온 계산
    List<Double> temperatures = weathers.stream()
        .map(Weather::getTemperature)
        .toList();
    Double minTemp = temperatureCalculator.calculateMin(temperatures);
    Double maxTemp = temperatureCalculator.calculateMax(temperatures);

    // 어제 기온 조회 및 어제 대비 차이 계산 (어제 동시간대 기준 24시간 전)
    Instant yesterdayTime = now.minus(Duration.ofHours(24));
    Optional<Weather> yesterdayWeather = weatherRepository
        .findTopByLatitudeAndLongitudeAndForecastAtLessThanEqualOrderByForecastAtDesc(latitude, longitude, yesterdayTime);

    Double currentTemp = weathers.get(0).getTemperature();
    Double yesterdayTemp = yesterdayWeather.map(Weather::getTemperature).orElse(null);
    Double tempDiff = temperatureCalculator.calculateDifference(currentTemp, yesterdayTemp);

    return weathers.stream()
        .map(weather -> WeatherDto.from(weather, location,minTemp, maxTemp, tempDiff))
        .toList();
  }
}