package com.otboo.team03.weather.service;

import com.otboo.team03.profile.dto.WeatherAPILocation;
import com.otboo.team03.weather.dto.WeatherDto;
import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.exception.WeatherNotFoundException;
import com.otboo.team03.weather.repository.WeatherRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeatherServiceImpl implements WeatherService {

  private final LocationService locationService;
  private final WeatherRepository weatherRepository;

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

    return weathers.stream()
        .map(weather -> WeatherDto.from(weather, location))
        .toList();
  }
}