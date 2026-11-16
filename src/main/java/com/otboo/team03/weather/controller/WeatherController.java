package com.otboo.team03.weather.controller;

import com.otboo.team03.profile.dto.WeatherAPILocation;
import com.otboo.team03.weather.dto.WeatherDto;
import com.otboo.team03.weather.service.LocationService;
import com.otboo.team03.weather.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "날씨 관리", description = "날씨 관리 API")
@RestController
@RequestMapping("/api/weathers")
@RequiredArgsConstructor
public class WeatherController {

  private final LocationService locationService;
  private final WeatherService weatherService;

  // 위도와 경도를 받아 기상청 격자(x, y)와 행정구역명이 포함된 위치 정보를 반환합니다.
  @GetMapping("/location")
  public ResponseEntity<WeatherAPILocation> getLocation(
      @RequestParam("latitude") Double latitude,
      @RequestParam("longitude") Double longitude
  ) {
    WeatherAPILocation location = locationService.getLocation(latitude, longitude);
    return ResponseEntity.ok(location);
  }

  @Operation(summary = "날씨 정보 조회", description = "위도/경도 기반 날씨 정보 목록(현재 및 시간대별 예보)을 조회합니다.")
  @GetMapping
  public ResponseEntity<List<WeatherDto>> getWeather(
      @RequestParam Double longitude,
      @RequestParam Double latitude
  ) {
    List<WeatherDto> response = weatherService.getWeathers(longitude, latitude);
    return ResponseEntity.ok(response);
  }
}
