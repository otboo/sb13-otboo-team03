package com.otboo.team03.weather.service;

import com.otboo.team03.weather.dto.HourlyForecastDto;
import com.otboo.team03.weather.dto.WeatherResponse;
import com.otboo.team03.weather.exception.WeatherNotFoundException;
import com.otboo.team03.weather.repository.WeatherRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeatherServiceImpl implements WeatherService {

  private final WeatherRepository weatherRepository;

  @Override
  public WeatherResponse getWeather(Double latitude, Double longitude) {
    Instant now = Instant.now();

    // 1.현재 날씨 단건 조회 (도우미 메서드 호출)
    HourlyForecastDto currentWeather = getCurrentWeather(latitude, longitude, now);

    // 2.향후 24시간 예보 목록 조회 (도우미 메서드 호출)
    List<HourlyForecastDto> hourlyForecasts = getHourlyForecasts(latitude, longitude, now);

    // 3.최종 응답 조립 및 반환
    return WeatherResponse.of(currentWeather, hourlyForecasts);
  }

  // 현재 날씨 1건 조회 및 DTO 변환 (없으면 예외 발생)
  private HourlyForecastDto getCurrentWeather(Double latitude, Double longitude, Instant now) {
    return weatherRepository
        .findTopByLatitudeAndLongitudeAndForecastAtLessThanEqualOrderByForecastAtDesc(latitude, longitude, now)
        .map(HourlyForecastDto::from)
        .orElseThrow(WeatherNotFoundException::new);
  }

  // 향후 24시간 예보 목록 조회 및 DTO 리스트 변환
  private List<HourlyForecastDto> getHourlyForecasts(Double latitude, Double longitude, Instant now) {
    Instant end = now.plus(24, ChronoUnit.HOURS);
    return weatherRepository
        .findAllByLatitudeAndLongitudeAndForecastAtBetweenOrderByForecastAtAsc(latitude, longitude, now, end)
        .stream()
        .map(HourlyForecastDto::from)
        .toList();
  }


}