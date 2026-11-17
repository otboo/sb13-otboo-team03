package com.otboo.team03.feed.repository;

import com.otboo.team03.feed.dto.response.PrecipitationDto;
import com.otboo.team03.feed.dto.response.TemperatureDto;
import com.otboo.team03.feed.dto.response.WeatherSummaryDto;
import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.exception.WeatherNotFoundException;
import com.otboo.team03.weather.repository.WeatherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class FeedWeatherLookupRepository {

  private final WeatherRepository weatherRepository;

  public boolean existsById(UUID weatherId) {
    return weatherRepository.existsById(weatherId);
  }

  public WeatherSummaryDto findSummaryById(UUID weatherId) {
    Weather weather = weatherRepository.findById(weatherId)
        .orElseThrow(WeatherNotFoundException::new);

    return new WeatherSummaryDto(
        weather.getId(),
        weather.getSkyStatus().name(),
        new PrecipitationDto(
            weather.getPrecipitationType().name(),
            weather.getPrecipitationAmount(),
            weather.getPrecipitationProbability()
        ),
        new TemperatureDto(
            weather.getTemperature(),
            null,
            null,
            null
        )
    );
  }
}
